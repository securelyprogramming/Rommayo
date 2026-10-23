'use strict';
const { app, BrowserWindow, WebContentsView, ipcMain, protocol, net, session, dialog, shell, Menu } = require('electron');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { normalizeServer, isWebUrl, sameOrigin, partitionFor } = require('./policy.cjs');

protocol.registerSchemesAsPrivileged([{ scheme: 'rommayo', privileges: { standard: true, secure: true, supportFetchAPI: true } }]);
if (process.env.ROMMAYO_USER_DATA_DIRECTORY) app.setPath('userData', process.env.ROMMAYO_USER_DATA_DIRECTORY);
let window, remote, server = '', servers = [], connecting = false, temporaryServers = [], lastSession = null, quitting = false;
let controlsOpen = false;
const { playerAction } = require('./player-actions.cjs');
const deck = process.argv.includes('--deck');
const shellUrl = 'rommayo://app/index.html';
function configPath() { return path.join(app.getPath('userData'), 'servers.json'); }
function save() { fs.writeFileSync(configPath(), JSON.stringify({ servers, temporaryServers, lastSession }, null, 2), { mode: 0o600 }); }
function state(error) { return { connected: !!remote, server, servers, error, desktop: true, deck, controlsOpen }; }
function sendState(error) { if (window && !window.isDestroyed()) window.webContents.send('rommayo:state', state(error)); }
function layout() {
  if (!remote) return;
  const [width, height] = window.getContentSize();
  const top = window.isFullScreen() ? 0 : 64;
  remote.setBounds({ x: 0, y: top, width, height: Math.max(1, height - top - (controlsOpen ? 244 : 68)) });
}
function closeRemote() {
  if (!remote) return;
  controlsOpen = false;
  const old = remote; remote = null;
  window.contentView.removeChildView(old);
  old.webContents.close();
  window.setTitle('Rommayo'); sendState();
}
function switchServer() { lastSession = null; save(); closeRemote(); }
async function confirmExternal(url) {
  if (!isWebUrl(url)) return;
  const origin = new URL(url).origin;
  const result = await dialog.showMessageBox(window, {
    type: 'question', title: 'Leave your RomM server?',
    message: `Open ${origin}?`, detail: 'For single sign-on, open here. Only continue to a site you trust.',
    buttons: ['Cancel', 'Open in browser', 'Open here'], defaultId: 0, cancelId: 0
  });
  if (result.response === 1) await shell.openExternal(url);
  else if (result.response === 2 && remote) {
    remote.allowedOrigins.add(origin);
    await remote.webContents.loadURL(url).catch(() => {});
  }
}
function configureSession(ses) {
  if (ses.rommayoConfigured) return;
  ses.rommayoConfigured = true;
  // Web games can request fullscreen and pointer lock. Other device access stays denied.
  ses.setPermissionRequestHandler((contents, permission, callback, details) => {
    callback(!!remote && contents === remote.webContents && sameOrigin(details.requestingUrl || contents.getURL(), server) && ['fullscreen', 'pointerLock'].includes(permission));
  });
  ses.setPermissionCheckHandler((contents, permission, origin) => !!remote && contents === remote.webContents && sameOrigin(origin, server) && ['fullscreen', 'pointerLock'].includes(permission));
  ses.on('will-download', (_event, item) => {
    item.setSaveDialogOptions({ title: 'Save from your RomM server', defaultPath: path.join(app.getPath('downloads'), path.basename(item.getFilename())) });
    item.once('done', (_event, status) => {
      if (status !== 'completed' && status !== 'cancelled' && window && !window.isDestroyed()) {
        dialog.showMessageBox(window, { type: 'error', message: 'The download did not finish.', detail: 'Retry from RomM and check your server connection.' });
      }
    });
  });
}
async function clearAuthentication(address) {
  const ses = session.fromPartition(partitionFor(address));
  await ses.clearStorageData({ storages: ['cookies'] });
  await ses.cookies.flushStore();
}
async function quitApp() {
  if (remote) {
    const choice = await dialog.showMessageBox(window, { type: 'question', message: 'Quit Rommayo?', detail: 'Save or export your game first. Quitting keeps a remembered login for next time.', buttons: ['Stay', 'Quit app'], defaultId: 0, cancelId: 0 });
    if (choice.response !== 1) return;
  }
  quitting = true; app.quit();
}
async function logOut() {
  if (!remote || connecting) return;
  const choice = await dialog.showMessageBox(window, { type: 'question', message: 'Log out of RomM?', detail: 'Save your game before leaving. This ends this device’s session and preserves local game saves.', buttons: ['Stay', 'Log out'], defaultId: 0, cancelId: 0 });
  if (choice.response !== 1) return;
  const address = server, ses = remote.webContents.session;
  connecting = true;
  try {
    closeRemote();
    const controller = new AbortController(), timeout = setTimeout(() => controller.abort(), 10000);
    try {
      const heartbeat = await ses.fetch(new URL('api/heartbeat', address).href, { credentials: 'include', redirect: 'error', signal: controller.signal }); await heartbeat.arrayBuffer();
      const token = (await ses.cookies.get({ url: address })).find(cookie => cookie.name === 'romm_csrftoken')?.value;
      const response = await ses.fetch(new URL('api/logout', address).href, { method: 'POST', credentials: 'include', redirect: 'error', signal: controller.signal, headers: token ? { 'x-csrftoken': token } : {} }); await response.arrayBuffer();
    } catch { /* Local sign-out must work offline. */ } finally { clearTimeout(timeout); }
    await clearAuthentication(address);
    servers = servers.filter(value => value !== address); temporaryServers = temporaryServers.filter(value => value !== address); server = ''; lastSession = null; save();
  } finally { connecting = false; sendState(); }
}
async function connect(command) {
  if (connecting) throw new Error('A connection is already in progress.');
  const address = normalizeServer(command.server);
  if (command.consent !== true) throw new Error('Confirm that you trust this server and understand the content notice.');
  if (new URL(address).protocol === 'http:' && command.allowHttp !== true) throw new Error('Confirm the unencrypted HTTP connection.');
  if (!command.webLogin && (typeof command.username !== 'string' || !command.username || command.username.includes(':') || typeof command.password !== 'string' || !command.password || command.username.length > 256 || command.password.length > 4096)) {
    throw new Error('Enter your RomM username and password, or use RomM sign-in.');
  }
  connecting = true;
  try {
    const ses = session.fromPartition(partitionFor(address));
    configureSession(ses);
    // Persist cleanup intent before the upstream can issue persistent cookies.
    const remember = command.rememberLogin !== false;
    lastSession = null;
    temporaryServers = temporaryServers.filter(value => value !== address);
    if (!remember) { temporaryServers.push(address); servers = servers.filter(value => value !== address); }
    save();
    if (!command.webLogin) {
      // RomM may prefer a stale session cookie over freshly supplied Basic credentials.
      // An explicit username/password login starts a fresh session for this server.
      for (const cookie of await ses.cookies.get({ url: address })) {
        await ses.cookies.remove(address, cookie.name);
      }
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 20000);
      try {
        const response = await ses.fetch(new URL('api/login', address).href, {
          method: 'POST', redirect: 'error', credentials: 'include', signal: controller.signal,
          headers: { Authorization: `Basic ${Buffer.from(`${command.username}:${command.password}`, 'utf8').toString('base64')}` }
        });
        if (!response.ok) throw new Error(response.status === 401 || response.status === 403 ? 'RomM rejected this username or password.' : `RomM sign-in failed (HTTP ${response.status}). Try RomM sign-in for SSO.`);
        await response.arrayBuffer();
        await ses.cookies.flushStore();
      } catch (error) {
        if (error.message?.startsWith('RomM')) throw error;
        throw new Error('Could not reach RomM. Check the address, network and server certificate.');
      } finally { clearTimeout(timeout); }
    }
    closeRemote(); server = address;
    remote = new WebContentsView({ webPreferences: {
      preload: path.join(__dirname, 'player-preload.cjs'), session: ses, nodeIntegration: false, contextIsolation: true, sandbox: true,
      webSecurity: true, allowRunningInsecureContent: false, autoplayPolicy: 'user-gesture-required'
    } });
    const view = remote;
    const skin = (command.steamTheme !== false ? fs.readFileSync(path.join(__dirname, '..', 'shared', 'rommayo-theme.css'), 'utf8') : '') + (deck ? fs.readFileSync(path.join(__dirname, '..', 'shared', 'deck-player.css'),'utf8') : '');
    view.webContents.on('did-finish-load', () => {
      if (sameOrigin(view.webContents.getURL(), address)) {
        if (skin) view.webContents.insertCSS(skin).catch(() => {});
        view.webContents.executeJavaScript(fs.readFileSync(path.join(__dirname, '..', 'shared', 'mayo-player.js'), 'utf8')).catch(() => {});
      }
    });
    view.allowedOrigins = new Set([new URL(address).origin]);
    window.contentView.addChildView(view); layout();
    const guard = (event, url) => {
      if (!isWebUrl(url) || !view.allowedOrigins.has(new URL(url).origin)) {
        event.preventDefault(); confirmExternal(url);
      }
    };
    view.webContents.on('will-navigate', guard);
    view.webContents.on('will-redirect', guard);
    view.webContents.setWindowOpenHandler(({ url }) => {
      if (isWebUrl(url) && sameOrigin(url, address)) {
        // Preserve RomM's target=_blank play, manual and download routes in this session.
        setImmediate(() => !view.webContents.isDestroyed() && view.webContents.loadURL(url).catch(() => {}));
      } else confirmExternal(url);
      return { action: 'deny' };
    });
    view.webContents.on('page-title-updated', (event, title) => { event.preventDefault(); window.setTitle(`${title || 'RomM'} · Rommayo`); });
    view.webContents.on('will-prevent-unload', (event) => {
      if (quitting || view.playerExited) { event.preventDefault(); return; }
      const choice = dialog.showMessageBoxSync(window, {
        type: 'question', title: 'Leave this RomM page?',
        message: 'Your game or unsaved changes may still be running.',
        detail: 'Save in RomM before leaving if you want to keep your progress.',
        buttons: ['Stay', 'Leave page'], defaultId: 0, cancelId: 0
      });
      if (choice === 1) event.preventDefault();
    });
    view.webContents.on('enter-html-full-screen', () => { window.setFullScreen(true); layout(); });
    view.webContents.on('leave-html-full-screen', () => { if (!deck) window.setFullScreen(false); layout(); });
    view.webContents.on('render-process-gone', () => { if (remote === view) { closeRemote(); sendState('The web engine stopped. Reconnect to your server.'); } });
    view.webContents.on('before-input-event', (event, input) => {
      if (input.type !== 'keyDown') return;
      if (input.key === 'F10') { event.preventDefault(); controlsOpen = !controlsOpen; if (controlsOpen) window.webContents.focus(); layout(); sendState(); }
      if (input.key === 'F11') { event.preventDefault(); window.setFullScreen(!window.isFullScreen()); layout(); }
      if (input.key === 'Escape' && window.isFullScreen()) { window.setFullScreen(false); layout(); }
      if (input.alt && input.key === 'ArrowLeft' && view.webContents.navigationHistory.canGoBack()) { event.preventDefault(); view.webContents.navigationHistory.goBack(); }
      if ((input.control || input.meta) && input.shift && input.key.toLowerCase() === 's') { event.preventDefault(); switchServer(); }
    });
    await view.webContents.loadURL(address);
    servers = command.rememberLogin === false ? servers.filter(s => s !== address) : [address, ...servers.filter(s => s !== address)].slice(0, 8);
    lastSession = command.rememberLogin === false ? null : { server: address, steamTheme: command.steamTheme !== false }; save();
    view.webContents.focus(); sendState();
    return state();
  } catch (error) {
    closeRemote();
    throw new Error(error.message?.startsWith('RomM') || error.message?.startsWith('Could not') ? error.message : 'Could not load this server. Check its address and network connection.');
  } finally {
    command.password = ''; connecting = false;
  }
}
async function command(event, payload) {
  if (event.sender !== window.webContents || event.senderFrame?.url !== shellUrl) throw new Error('Untrusted command source.');
  if (!payload || typeof payload.type !== 'string') throw new Error('Invalid command.');
  switch (payload.type) {
    case 'init': return state();
    case 'connect': return connect(payload);
    case 'back': if (remote?.webContents.navigationHistory.canGoBack()) remote.webContents.navigationHistory.goBack(); break;
    case 'home': if (remote) await remote.webContents.loadURL(server); break;
    case 'reload': remote?.webContents.reload(); break;
    case 'fullscreen': window.setFullScreen(!window.isFullScreen()); layout(); break;
    case 'disconnect': switchServer(); break;
    case 'quit': await quitApp(); break;
    case 'controls': controlsOpen = !!payload.open && !!remote; if (controlsOpen) window.webContents.focus(); else remote?.webContents.focus(); layout(); break;
    case 'player-action': {
      if (!remote || !sameOrigin(remote.webContents.getURL(),server)) throw new Error('Open a game first.');
      if (['loadState','loadSavFiles','restart'].includes(payload.action)) {
        const choice = await dialog.showMessageBox(window,{type:'question',message:'Replace current game progress?',detail:'Save or export first. This action may replace your current progress.',buttons:['Cancel','Continue'],defaultId:0,cancelId:0});
        if (choice.response !== 1) return state();
      }
      const script = playerAction(payload.action);
      let done = false;
      for (const frame of remote.webContents.mainFrame.framesInSubtree) {
        if (!sameOrigin(frame.url,server)) continue;
        try { if (await frame.executeJavaScript(script,true)) { done = true; break; } } catch { /* Try the next same-origin player frame. */ }
      }
      if (!done) throw new Error('This action needs a running EmulatorJS game with that control enabled. Use the player’s own toolbar for other engines.');
      break;
    }
    case 'logout': await logOut(); break;
    case 'forget': {
      const choice = await dialog.showMessageBox(window, { type: 'question', message: 'Forget all saved RomM sessions?', detail: 'This signs this app out of every saved server. Local game saves and exported files are preserved.', buttons: ['Cancel', 'Forget sessions'], defaultId: 0, cancelId: 0 });
      if (choice.response !== 1) break;
      closeRemote();
      for (const saved of new Set([...servers, ...temporaryServers, ...(server ? [server] : [])])) {
        await clearAuthentication(saved);
      }
      servers = []; temporaryServers = []; server = ''; lastSession = null; save(); break;
    }
    default: throw new Error('Unknown command.');
  }
  sendState(); return state();
}
app.whenReady().then(async () => {
  protocol.handle('rommayo', (request) => {
    const url = new URL(request.url);
    const allowed = ['index.html', 'style.css', 'app.js', 'touch-controls.js'];
    const name = url.pathname.slice(1);
    if (url.hostname !== 'app' || !allowed.includes(name)) return new Response('Not found', { status: 404 });
    return net.fetch(pathToFileURL(path.join(__dirname, '..', 'shared', name)).href);
  });
  try {
    const config = JSON.parse(fs.readFileSync(configPath(), 'utf8'));
    servers = config.servers.map(normalizeServer).slice(0, 8);
    temporaryServers = (config.temporaryServers || []).map(normalizeServer);
    // Older versions saved recent servers only after consent. Migrate the most recent.
    const remembered = Object.hasOwn(config, 'lastSession') ? config.lastSession : servers.length ? { server: servers[0], steamTheme: true } : null;
    if (remembered && servers.includes(normalizeServer(remembered.server))) lastSession = { server: normalizeServer(remembered.server), steamTheme: remembered.steamTheme !== false };
  } catch { servers = []; temporaryServers = []; lastSession = null; }
  for (const address of temporaryServers) { await clearAuthentication(address); if (lastSession?.server === address) lastSession = null; servers = servers.filter(value => value !== address); }
  temporaryServers = []; save();
  ipcMain.on('rommayo:player-exit', (event) => {
    const view = remote;
    if (!view || event.sender !== view.webContents || event.senderFrame !== view.webContents.mainFrame || !sameOrigin(event.senderFrame.url, server) || view.playerExited) return;
    // Upstream already confirmed Exit. Keep this session and return to its library.
    view.playerExited = true;
    view.webContents.loadURL(server).catch(() => sendState('Could not return to your library. Use Home to retry.')).finally(() => { view.playerExited = false; });
  });
  ipcMain.handle('rommayo:command', command);
  function createWindow() {
    window = new BrowserWindow({ width: 1200, height: 920, minWidth: 360, minHeight: 500, backgroundColor: '#101413', title: 'Rommayo', icon: path.join(__dirname, '..', 'assets', 'icon.png'),
      webPreferences: { preload: path.join(__dirname, 'preload.cjs'), nodeIntegration: false, contextIsolation: true, sandbox: true } });
    window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
    window.webContents.on('will-navigate', (event, url) => { if (url !== shellUrl) event.preventDefault(); });
    window.on('resize', layout); window.on('enter-full-screen', layout); window.on('leave-full-screen', layout);
    window.on('closed', () => { if (remote && !remote.webContents.isDestroyed()) remote.webContents.close(); remote = null; });
    window.loadURL(shellUrl).then(async () => {
      if (!lastSession) return;
      const restore = lastSession;
      try { await connect({ type: 'connect', ...restore, webLogin: true, consent: true, allowHttp: true, rememberLogin: true }); }
      catch { lastSession = restore; save(); sendState('Could not reopen your saved server. Check your connection or choose a server below.'); }
    });
    if (deck) window.setFullScreen(true);
  }
  Menu.setApplicationMenu(Menu.buildFromTemplate([
    ...(process.platform === 'darwin' ? [{ role: 'appMenu' }] : []),
    { label: 'Rommayo', submenu: [
      { label: 'Log out', click: () => logOut().catch(() => sendState('Could not finish signing out. Please retry.')) },
      { label: 'Switch server', accelerator: 'CmdOrCtrl+Shift+S', click: switchServer },
      { label: 'RomM home', accelerator: 'CmdOrCtrl+Home', click: () => remote?.webContents.loadURL(server) },
      { label: 'Reload', accelerator: 'CmdOrCtrl+R', click: () => remote?.webContents.reload() },
      { label: 'Game controls (F10)', click: () => { controlsOpen = !controlsOpen && !!remote; if (controlsOpen) window.webContents.focus(); layout(); sendState(); } },
      { label: 'Fullscreen', accelerator: 'F11', click: () => { window.setFullScreen(!window.isFullScreen()); layout(); } },
      { type: 'separator' }, { label: 'Quit Rommayo', accelerator: 'CmdOrCtrl+Q', click: quitApp }
    ] }, { role: 'editMenu' }, { role: 'windowMenu' }
  ]));
  createWindow();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
