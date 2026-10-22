const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { createServer } = require('../web/server.cjs');
let upstream, proxy, base, upstreamUrl, acceptedLogouts = 0;
before(async () => {
  upstream = http.createServer((req, res) => {
    if (req.url === '/api/login') {
      if (req.headers.authorization !== `Basic ${Buffer.from('demo:demo').toString('base64')}`) { res.writeHead(401); return res.end('{}'); }
      res.setHeader('Set-Cookie', 'romm_session=demo-session; Domain=127.0.0.1; HttpOnly; SameSite=Lax; Max-Age=86400; Path=/'); return res.end('{}');
    }
    if (req.url === '/api/heartbeat') { res.setHeader('Set-Cookie', 'romm_csrftoken=test-csrf-token; Path=/'); return res.end('{}'); }
    if (req.url === '/api/logout') { if (req.headers['x-csrftoken'] !== 'test-csrf-token') { res.writeHead(403); return res.end('CSRF token verification failed'); } acceptedLogouts++; return res.end('{}'); }
    if (req.url === '/api/echo') { res.setHeader('Content-Type', 'application/json'); return res.end(JSON.stringify({ cookie: req.headers.cookie, origin: req.headers.origin, range: req.headers.range })); }
    if (req.url === '/upload') { let count = 0; req.on('data', (b) => count += b.length); return req.on('end', () => res.end(String(count))); }
    res.setHeader('Content-Type', 'text/html'); res.end('<html><head><title>RomM</title><link rel="manifest" href="/site.webmanifest"></head><body>Library</body></html>');
  });
  await new Promise((resolve) => upstream.listen(0, '127.0.0.1', resolve)); upstreamUrl = `http://127.0.0.1:${upstream.address().port}/`;
  proxy = createServer({ upstream: upstreamUrl }); await new Promise((resolve) => proxy.listen(0, '127.0.0.1', resolve)); base = `http://127.0.0.1:${proxy.address().port}`;
});
after(async () => { await Promise.all([new Promise((r) => proxy.close(r)), new Promise((r) => upstream.close(r))]); });
async function connect(overrides = {}, origin = base) {
  return fetch(base + '/__rommayo/connect', { method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json' }, body: JSON.stringify({ server: upstreamUrl, username: 'demo', password: 'demo', consent: true, allowHttp: true, steamTheme: true, ...overrides }) });
}
test('web connection forwards login and returns private browser cookies without passwords', async () => {
  const response = await connect(); assert.equal(response.status, 200);
  const cookies = response.headers.getSetCookie().join(';'); assert.match(cookies, /romm_session=demo-session/); assert.match(cookies, /HttpOnly/); assert.doesNotMatch(cookies, /Domain=/);
  assert.deepEqual(await response.json(), { redirect: '/' });
  const noSession = await fetch(base + '/api/echo'); assert.equal((await noSession.json()).cookie, undefined);
});
test('web connection rejects cross-site requests, arbitrary upstreams and bad credentials', async () => {
  assert.equal((await connect({}, 'https://untrusted.example')).status, 403);
  assert.equal((await connect({ server: 'http://untrusted.example' })).status, 400);
  assert.equal((await connect({ password: 'wrong' })).status, 401);
});
test('full interface HTML gets the selected theme and one Rommayo manifest', async () => {
  const response = await fetch(base + '/', { headers: { Cookie: 'rommayo_theme=1' } }); const html = await response.text();
  assert.match(html, /rommayo-theme.css/); assert.equal((html.match(/rel="manifest"/g) || []).length, 1);
  const unthemed = await (await fetch(base + '/')).text(); assert.doesNotMatch(unthemed, /rommayo-theme.css/);
});
test('proxy streams uploads and preserves cookie, origin and range behavior', async () => {
  const response = await fetch(base + '/api/echo', { headers: { Cookie: 'romm_session=own-session', Origin: base, Range: 'bytes=0-127' } });
  const values = await response.json(); assert.equal(values.cookie, 'romm_session=own-session'); assert.equal(values.origin, upstreamUrl.slice(0, -1)); assert.equal(values.range, 'bytes=0-127');
  const bytes = Buffer.alloc(2 * 1024 * 1024, 42); const upload = await fetch(base + '/upload', { method: 'POST', body: bytes }); assert.equal(await upload.text(), String(bytes.length));
});
test('connection page and assets are available without an upstream session', async () => {
  const page = await (await fetch(base + '/__rommayo/')).text(); assert.match(page, /web-bridge.js/); assert.match(page, /USERNAME/);
  const config = await (await fetch(base + '/__rommayo/config')).json(); assert.equal(config.configuredServer, upstreamUrl);
});

test('unchecked web remember produces session cookies and logout is same-origin only', async () => {
  const response = await connect({rememberLogin:false});
  const cookies = response.headers.getSetCookie();
  assert.doesNotMatch(cookies.find(value => value.startsWith('romm_session=')), /Max-Age|Expires/i);
  assert.match(cookies.join(';'), /rommayo_remember=0/);
  const bad = await fetch(base + '/__rommayo/logout', {method:'POST',headers:{Origin:'https://untrusted.example','Content-Type':'application/json'},body:'{}'});
  assert.equal(bad.status,403);
  const logout = await fetch(base + '/__rommayo/logout', {method:'POST',headers:{Origin:base,'Content-Type':'application/json',Cookie:'romm_session=demo-session; rommayo_remember=0'},body:'{}'});
  assert.equal(logout.status,200);assert.equal(acceptedLogouts,1);assert.match(logout.headers.getSetCookie().join(';'), /romm_session=; Max-Age=0/);
  assert.equal(logout.headers.get('Clear-Site-Data'),null);
  const html = await (await fetch(base + '/')).text(); assert.match(html, /__rommayo\/session.js/);
});

test('malformed origins and targets cannot terminate the web server',async()=>{
 for(const origin of ['not-a-url','null','http://[','https://user@'+new URL(base).host]){
  const response=await fetch(base+'/__rommayo/logout',{method:'POST',headers:{Origin:origin,'Content-Type':'application/json'},body:'{}'});assert.equal(response.status,403);
 }
 const code=await new Promise((resolve,reject)=>{const req=http.request({...new URL(base),hostname:'127.0.0.1',port:new URL(base).port,path:'http://['},res=>{res.resume();resolve(res.statusCode)});req.on('error',reject);req.end();});assert.equal(code,400);
 assert.equal((await fetch(base+'/__rommayo/config')).status,200);
 assert.equal((await fetch(base+'/__rommayo/touch-controls.js')).status,200);
});
