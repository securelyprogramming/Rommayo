const { test, expect, _electron } = require('@playwright/test');
const http = require('node:http');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
let fixture, address, app, shellPage, data, acceptedLogouts = 0;
test.beforeAll(async () => {
  fixture = http.createServer((req, res) => {
    if (req.url === '/api/login') {
      if (req.headers.authorization !== `Basic ${Buffer.from('demo:demo').toString('base64')}`) { res.writeHead(401); return res.end('{}'); }
      res.setHeader('Set-Cookie', 'romm_session=fixture-session; HttpOnly; SameSite=Lax; Max-Age=86400; Path=/'); res.setHeader('Content-Type', 'application/json'); return res.end('{}');
    }
    if (req.url === '/api/heartbeat') { res.setHeader('Set-Cookie', 'romm_csrftoken=test-csrf-token; Path=/'); return res.end('{}'); }
    if (req.url === '/api/logout') { if (req.headers['x-csrftoken'] !== 'test-csrf-token') { res.writeHead(403); return res.end('CSRF token verification failed'); } acceptedLogouts++; res.setHeader('Set-Cookie', 'romm_session=; Max-Age=0; Path=/'); return res.end('{}'); }
    if (req.url === '/fixture-download') { res.setHeader('Content-Disposition', 'attachment; filename="fixture.txt"'); return res.end('authorized fixture content'); }
    res.setHeader('Content-Type', 'text/html');
    if (!req.headers.cookie?.includes('romm_session=fixture-session')) return res.end('<h1>RomM sign-in</h1>');
    res.end('<html><head><title>Fixture library</title></head><body class="v-theme--dark"><main class="v-application"><h1>My RomM library</h1><a href="/collections">Collections</a><a href="/fixture-download">Save fixture</a><input type="file" multiple><button id="blob">Save blob</button></main><script>document.getElementById("blob").onclick=()=>{const a=document.createElement("a");a.download="save.txt";a.href=URL.createObjectURL(new Blob(["fixture save"]));a.click()};</script></body></html>');
  });
  await new Promise((r) => fixture.listen(0, '127.0.0.1', r)); address = `http://127.0.0.1:${fixture.address().port}`;
});
test.afterAll(async () => { if (fixture) await new Promise((r) => fixture.close(r)); });
test.beforeEach(async () => {
  data = fs.mkdtempSync(path.join(os.tmpdir(), 'rommayo-desktop-'));
  app = await _electron.launch({ args: [...(process.env.CI ? ['--no-sandbox'] : []), path.join(__dirname, '..')], env: { ...process.env, ROMMAYO_USER_DATA_DIRECTORY: data } });
  shellPage = await app.firstWindow(); await shellPage.locator('#server').waitFor();
});
test.afterEach(async () => { if (app) await app.close(); fs.rmSync(data, { recursive: true, force: true }); });
async function fill(password = 'demo') {
  await shellPage.locator('#server').fill(address);
  await shellPage.locator('#username').fill('demo'); await shellPage.locator('#password').fill(password);
  await shellPage.locator('#consent').check(); await shellPage.locator('#http-consent').check();
}
async function remotePage() {
  await expect.poll(() => app.context().pages().filter((p) => p.url().startsWith(address)).length).toBe(1);
  return app.context().pages().find((p) => p.url().startsWith(address));
}
test('login transfers HttpOnly session to isolated full RomM view; uploads and navigation work', async () => {
  await fill(); await shellPage.locator('.connect-button').click();
  const remote = await remotePage(); await expect(remote.locator('h1')).toHaveText('My RomM library');
  expect(await remote.evaluate(() => [typeof window.rommayo, typeof require, typeof process])).toEqual(['undefined', 'undefined', 'undefined']);
  expect(await remote.evaluate(() => document.cookie)).not.toContain('fixture-session');
  await expect(shellPage.locator('#password')).toHaveValue('');
  expect(await remote.locator('.v-application').evaluate((e) => getComputedStyle(e).backgroundImage)).toContain('linear-gradient');
  await remote.getByText('Collections', { exact: true }).click(); await expect(remote).toHaveURL(`${address}/collections`);
  await remote.locator('input[type=file]').setInputFiles([{ name: 'homebrew.txt', mimeType: 'text/plain', buffer: Buffer.from('test upload') }]);
  expect(await remote.locator('input[type=file]').evaluate((e) => e.files[0].name)).toBe('homebrew.txt');
  const config = fs.readFileSync(path.join(data, 'servers.json'), 'utf8');
  expect(config).not.toContain('password'); expect(config).not.toContain('fixture-session');
  await shellPage.locator('#switch').click(); await expect(shellPage.locator('#onboarding')).toBeVisible();
  await shellPage.locator('#web-login').click(); const reopened = await remotePage();
  await expect(reopened.locator('h1')).toHaveText('My RomM library');
});
test('bad credentials show a readable error and never open a remote view', async () => {
  await fill('wrong'); await shellPage.locator('.connect-button').click();
  await expect(shellPage.locator('#error')).toContainText('RomM rejected');
  expect(app.context().pages().filter((p) => p.url().startsWith(address))).toHaveLength(0);
});
test('server IPC independently rejects HTTP without explicit acknowledgement', async () => {
  const error = await shellPage.evaluate(async (url) => {
    try { await window.rommayo.send({ type: 'connect', server: url, consent: true, allowHttp: false, webLogin: true }); }
    catch (e) { return e.message; }
  }, address);
  expect(error).toContain('unencrypted HTTP');
});
test('session cookies survive app restart and server passwords are not needed again', async () => {
  await fill(); await shellPage.locator('.connect-button').click(); await remotePage();
  await app.close();
  app = await _electron.launch({ args: [...(process.env.CI ? ['--no-sandbox'] : []), path.join(__dirname, '..')], env: { ...process.env, ROMMAYO_USER_DATA_DIRECTORY: data } });
  shellPage = await app.firstWindow(); await shellPage.locator('#desktop-exit').waitFor();
  const remote = await remotePage();
  await expect(remote.locator('h1')).toHaveText('My RomM library');
});
test('HTTP and blob downloads use native save handling', async () => {
  await fill(); await shellPage.locator('.connect-button').click(); const remote = await remotePage();
  const output = path.join(data, 'saved-fixture.txt');
  const key = require('../desktop/policy.cjs').partitionFor(address);
  await app.evaluate(({ session }, { key, output }) => {
    session.fromPartition(key).on('will-download', (_e, item) => item.setSavePath(output));
  }, { key, output });
  await remote.getByText('Save fixture', { exact: true }).click();
  await expect.poll(() => fs.existsSync(output)).toBe(true); expect(fs.readFileSync(output, 'utf8')).toBe('authorized fixture content');
  fs.unlinkSync(output); await remote.locator('#blob').click();
  await expect.poll(() => fs.existsSync(output) ? fs.readFileSync(output, 'utf8') : null).toBe('fixture save');
});
test('leaving an active game asks first and respects both stay and leave', async () => {
  await fill(); await shellPage.locator('.connect-button').click(); const remote = await remotePage();
  await remote.getByText('Collections', { exact: true }).click();
  await remote.evaluate(() => { window.onbeforeunload = (event) => { event.preventDefault(); event.returnValue = ''; }; });
  await app.evaluate(({ dialog, BrowserWindow }) => {
    const win = BrowserWindow.getAllWindows()[0]; win.unloadPrompts = 0;
    dialog.showMessageBoxSync = () => { win.unloadPrompts++; return 0; };
  });
  await shellPage.evaluate(() => window.rommayo.send({ type: 'home' }).catch(() => {}));
  await expect(remote).toHaveURL(`${address}/collections`);
  expect(await app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].unloadPrompts)).toBe(1);
  await app.evaluate(({ dialog }) => { dialog.showMessageBoxSync = () => 1; });
  await shellPage.evaluate(() => window.rommayo.send({ type: 'home' }));
  await expect(remote).toHaveURL(`${address}/`);
});

test('unchecked remember login requires sign-in after restart while preserving game storage', async () => {
  await fill(); await shellPage.locator('#remember-login').uncheck(); await shellPage.locator('.connect-button').click();
  let remote = await remotePage(); await expect(remote.locator('h1')).toHaveText('My RomM library');
  await remote.evaluate(() => localStorage.setItem('game-save-fixture','keep-progress'));
  await app.close();
  app = await _electron.launch({ args: [...(process.env.CI ? ['--no-sandbox'] : []), path.join(__dirname, '..')], env: { ...process.env, ROMMAYO_USER_DATA_DIRECTORY: data } });
  shellPage = await app.firstWindow(); await shellPage.locator('#server').waitFor();
  await expect(shellPage.locator('.saved-server')).toHaveCount(0);
  await fill(); await shellPage.locator('#web-login').click(); remote = await remotePage();
  await expect(remote.locator('h1')).toHaveText('RomM sign-in');
  expect(await remote.evaluate(() => localStorage.getItem('game-save-fixture'))).toBe('keep-progress');
});
test('logout respects cancellation and clears authentication without deleting game storage', async () => {
  await fill(); await shellPage.locator('.connect-button').click(); let remote = await remotePage();
  await remote.evaluate(() => localStorage.setItem('game-save-fixture','keep-progress'));
  await app.evaluate(({dialog}) => { dialog.showMessageBox = async () => ({response:0}); });
  await shellPage.locator('#logout').click(); await expect(remote.locator('h1')).toHaveText('My RomM library');
  await app.evaluate(({dialog}) => { dialog.showMessageBox = async () => ({response:1}); });
  const before = acceptedLogouts;
  await shellPage.locator('#logout').click(); await expect(shellPage.locator('#onboarding')).toBeVisible();
  expect(acceptedLogouts).toBe(before + 1);
  await expect(shellPage.locator('.saved-server')).toHaveCount(0);
  await shellPage.locator('#web-login').click(); remote = await remotePage();
  await expect(remote.locator('h1')).toHaveText('RomM sign-in');
  expect(await remote.evaluate(() => localStorage.getItem('game-save-fixture'))).toBe('keep-progress');
});

test('Deck keeps exit controls visible and quitting requires confirmation', async () => {
  await app.close();
  app = await _electron.launch({ args: [...(process.env.CI ? ['--no-sandbox'] : []), path.join(__dirname, '..'), '--deck'], env: { ...process.env, ROMMAYO_USER_DATA_DIRECTORY: data } });
  shellPage = await app.firstWindow(); await fill(); await shellPage.locator('.connect-button').click(); const remote = await remotePage();
  await expect(shellPage.locator('#exit-hint')).toContainText('STEAM');
  expect(await app.evaluate(({ BrowserWindow }) => { const w=BrowserWindow.getAllWindows()[0], v=w.contentView.children[0]; return w.getContentSize()[1]-v.getBounds().height-v.getBounds().y; })).toBe(68);
  await app.evaluate(({dialog}) => { dialog.showMessageBox = async () => ({response:0}); });
  await shellPage.locator('#quit').click(); await expect(remote.locator('h1')).toHaveText('My RomM library');
  await app.evaluate(({dialog}) => { dialog.showMessageBox = async () => ({response:1}); });
  const appProcess=app.process(); const closed = app.waitForEvent('close'); await shellPage.locator('#quit').click({noWaitAfter:true}).catch(error=>{if(!/closed/.test(error.message))throw error;}); await closed; await expect.poll(()=>appProcess.exitCode).toBe(0);
});
test('expired remembered cookie shows upstream sign-in and never retries a stored password', async () => {
  await fill(); await shellPage.locator('#steam-theme').uncheck(); await shellPage.locator('.connect-button').click(); await remotePage();
  await app.evaluate(async ({session}, key) => {const s=session.fromPartition(key);await s.clearStorageData({storages:['cookies']});await s.cookies.flushStore();}, require('../desktop/policy.cjs').partitionFor(address));
  await app.close();
  app = await _electron.launch({args:[...(process.env.CI?['--no-sandbox']:[]),path.join(__dirname,'..')],env:{...process.env,ROMMAYO_USER_DATA_DIRECTORY:data}});
  shellPage=await app.firstWindow(); const remote=await remotePage(); await expect(remote.locator('h1')).toHaveText('RomM sign-in');
  const config=JSON.parse(fs.readFileSync(path.join(data,'servers.json'))); expect(config.lastSession.steamTheme).toBe(false); expect(JSON.stringify(config)).not.toContain('demo');
});
test('touch and controller menu actions use upstream player buttons and confirm destructive actions',async()=>{
 await fill();await shellPage.locator('.connect-button').click();const remote=await remotePage();
 await remote.evaluate(()=>{window.actions=[];const button=name=>{const b=document.createElement('button');b.onclick=()=>window.actions.push(name);document.body.append(b);return b;};window.EJS_emulator={started:true,elements:{bottomBar:{playPause:[button('pause')],saveState:[button('save')],loadState:[button('load')]}}};});
 await shellPage.locator('#controls-toggle').click();await expect(shellPage.locator('#player-actions')).toBeVisible();
 await shellPage.locator('[data-player-action="saveState"]').click();expect(await remote.evaluate(()=>window.actions)).toEqual(['save']);
 await app.evaluate(({dialog})=>{dialog.showMessageBox=async()=>({response:0});});
 await shellPage.locator('[data-player-action="loadState"]').click();expect(await remote.evaluate(()=>window.actions)).toEqual(['save']);
 await app.evaluate(({dialog})=>{dialog.showMessageBox=async()=>({response:1});});
 await shellPage.locator('[data-player-action="loadState"]').click();expect(await remote.evaluate(()=>window.actions)).toEqual(['save','load']);
 // Standard controller: B closes, L3+R3 opens, A activates the focused action.
 await shellPage.evaluate(()=>{window.pad={index:0,mapping:'standard',buttons:Array.from({length:16},()=>({pressed:false}))};Object.defineProperty(navigator,'getGamepads',{value:()=>[window.pad]});window.pad.buttons[1].pressed=true;});
 await expect(shellPage.locator('#player-actions')).toBeHidden();
 await shellPage.evaluate(()=>{window.pad.buttons[1].pressed=false;window.pad.buttons[10].pressed=true;window.pad.buttons[11].pressed=true;});
 await expect(shellPage.locator('#player-actions')).toBeVisible();
 await shellPage.locator('[data-player-action="playPause"]').focus();await shellPage.evaluate(()=>{window.pad.buttons[0].pressed=true;});
 await expect.poll(()=>remote.evaluate(()=>window.actions)).toEqual(['save','load','pause']);
});

test('RomM player returns home after confirmed exit; touch toggle survives fullscreen and SPA replacement',async()=>{
 await fill();await shellPage.locator('.connect-button').click();const page=await remotePage();
 await page.getByText('Collections',{exact:true}).click();
 await page.evaluate(() => {
 const parent = document.createElement('div'); parent.id='engine'; parent.tabIndex=-1; parent.style.cssText='position:relative;width:600px;height:300px';
 const pad = document.createElement('div'); pad.className='ejs_virtualGamepad_parent'; pad.textContent='Touch D-pad';
 const toolbar=document.createElement('button'); toolbar.id='original-toolbar'; toolbar.textContent='Save state';
 parent.append(pad,toolbar); document.body.prepend(parent);
 const handlers={}; window.EJS_emulator={started:true,virtualGamepad:pad,elements:{parent},toggleVirtualGamepad:show=>pad.style.display=show?'':'none',on:(event,callback)=>(handlers[event]??=[]).push(callback),callEvent:event=>(handlers[event]??[]).forEach(callback=>callback())};
});
 await expect(page.getByRole('button',{name:'Hide touch controls'})).toBeVisible();
 await page.getByRole('button',{name:'Hide touch controls'}).click();
 await expect(page.locator('.ejs_virtualGamepad_parent')).toBeHidden();await expect(page.locator('#original-toolbar')).toBeVisible();
 // Fullscreen contains the toggle, independent of the outer app toolbar.
 expect(await page.locator('#engine .mayo-touch-toggle').count()).toBe(1);
 await page.evaluate(()=>{window.onbeforeunload=event=>{event.preventDefault();event.returnValue='';};window.EJS_emulator.callEvent('exit');});
 await expect(page).toHaveURL(address+'/');await expect(page.locator('#engine')).toHaveCount(0);
 await page.evaluate(() => {
 const parent = document.createElement('div'); parent.id='engine'; parent.tabIndex=-1; parent.style.cssText='position:relative;width:600px;height:300px';
 const pad = document.createElement('div'); pad.className='ejs_virtualGamepad_parent'; pad.textContent='Touch D-pad';
 const toolbar=document.createElement('button'); toolbar.id='original-toolbar'; toolbar.textContent='Save state';
 parent.append(pad,toolbar); document.body.prepend(parent);
 const handlers={}; window.EJS_emulator={started:true,virtualGamepad:pad,elements:{parent},toggleVirtualGamepad:show=>pad.style.display=show?'':'none',on:(event,callback)=>(handlers[event]??=[]).push(callback),callEvent:event=>(handlers[event]??[]).forEach(callback=>callback())};
});
 await expect(page.getByRole('button',{name:'Show touch controls'})).toBeVisible();
 await expect(page.locator('.ejs_virtualGamepad_parent')).toBeHidden();
 await page.evaluate(()=>{const old=window.EJS_emulator;const parent=old.elements.parent.cloneNode(true);parent.querySelector('.mayo-touch-toggle').remove();old.elements.parent.replaceWith(parent);window.EJS_emulator={...old,elements:{parent},virtualGamepad:parent.querySelector('.ejs_virtualGamepad_parent'),on:()=>{},toggleVirtualGamepad(show){this.virtualGamepad.style.display=show?'':'none';}};});
 await expect(page.locator('.mayo-touch-toggle')).toHaveCount(1);await expect(page.getByRole('button',{name:'Show touch controls'})).toBeVisible();
});
