'use strict';
// Single-upstream, streaming reverse proxy. Each browser signs into RomM independently.
// The upstream is chosen by the operator, never by an unauthenticated request.
const http = require('node:http');
const https = require('node:https');
const fs = require('node:fs');
const path = require('node:path');
const { normalizeServer } = require('../desktop/policy.cjs');

function createServer(config) {
  const upstream = new URL(normalizeServer(config.upstream));
  if (upstream.pathname !== '/') throw new Error('The web edition currently requires a RomM upstream at the root path. Use a dedicated hostname.');
  const transport = upstream.protocol === 'https:' ? https : http;
  const shared = path.join(__dirname, '..', 'shared');
  const assets = path.join(__dirname, '..', 'assets');
  const json = (res, code, data) => { res.writeHead(code, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(data)); };
  const cookieHeaders = (cookies, remember = true) => (cookies || []).map(value => { const local = value.replace(/;\s*Domain=[^;]+/gi, ''); return remember ? local : local.replace(/;\s*(?:Max-Age|Expires)=[^;]+/gi, ''); });
  function headers(req) {
    const values = { ...req.headers, host: upstream.host, 'accept-encoding': 'identity' };
    // RomM sees the same origin it would see without the companion proxy.
    if (values.origin) values.origin = upstream.origin;
    if (values.referer) { try { const url = new URL(values.referer); values.referer = upstream.origin + url.pathname + url.search; } catch { delete values.referer; } }
    // Do not trust proxy hints supplied by visitors.
    delete values['x-forwarded-host']; delete values['x-forwarded-proto']; delete values['x-forwarded-for'];
    return values;
  }
  function responseHeaders(response, req) {
    const values = { ...response.headers };
    if (values['set-cookie']) values['set-cookie'] = cookieHeaders(values['set-cookie'], !/(?:^|;\s*)rommayo_remember=0(?:;|$)/.test(req.headers.cookie || ''));
    if (values.location?.startsWith(upstream.origin + '/')) values.location = values.location.slice(upstream.origin.length);
    return values;
  }
  function ownOrigin(req) { try { const origin = new URL(req.headers.origin); return ['http:', 'https:'].includes(origin.protocol) && !origin.username && !origin.password && origin.host === req.headers.host && origin.pathname === '/' && !origin.search && !origin.hash; } catch { return false; } }
  const server = http.createServer(async (req, res) => {
    let route;
    try { route = new URL(req.url, 'http://rommayo.invalid').pathname; } catch { return json(res,400,{error:'Invalid request address.'}); }
    if (route.startsWith('/__rommayo/')) {
      res.setHeader('Cache-Control', 'no-store');
      if (req.method === 'GET' && route === '/__rommayo/config') return json(res, 200, { connected: false, servers: [upstream.href], configuredServer: upstream.href });
      if (req.method === 'POST' && route === '/__rommayo/logout') {
        if (!ownOrigin(req) || !req.headers['content-type']?.startsWith('application/json')) return json(res, 403, { error: 'Open Rommayo directly to log out.' });
        req.resume();
        const csrf = (req.headers.cookie || '').split(';').map(value => value.trim()).find(value => value.startsWith('romm_csrftoken='))?.slice('romm_csrftoken='.length);
        const expired = (req.headers.cookie || '').split(';').map(value => value.split('=')[0].trim()).filter(name => /^[!#$%&'*+.^_`|~0-9A-Za-z-]+$/.test(name)).map(name => `${name}=; Max-Age=0; Path=/; SameSite=Lax; HttpOnly`);
        let finished = false;
        const finish = () => { if (finished) return; finished = true; res.setHeader('Set-Cookie', expired); json(res, 200, { redirect: '/__rommayo/' }); };
        // Match RomM’s own client: refresh a user-bound CSRF cookie before POST.
        const heartbeat = transport.request(new URL('/api/heartbeat', upstream), { headers: { Cookie: req.headers.cookie || '' }, timeout: 10000 }, response => {
          response.resume();
          response.on('end', () => {
            const cookieMap = new Map((req.headers.cookie || '').split(';').map(value => { const index = value.indexOf('='); return [value.slice(0, index).trim(), value.slice(index + 1)]; }).filter(([name]) => name));
            for (const value of response.headers['set-cookie'] || []) { const item = value.split(';')[0], index = item.indexOf('='); cookieMap.set(item.slice(0, index), item.slice(index + 1)); }
            const token = cookieMap.get('romm_csrftoken') || csrf;
            const logout = transport.request(new URL('/api/logout', upstream), { method: 'POST', headers: { Cookie: [...cookieMap].map(([name, value]) => `${name}=${value}`).join('; '), ...(token ? { 'x-csrftoken': token } : {}), 'Content-Length': '0' }, timeout: 10000 }, result => { result.resume(); result.on('end', finish); });
            logout.on('timeout', () => logout.destroy()); logout.on('error', finish); logout.end();
          });
        });
        heartbeat.on('timeout', () => heartbeat.destroy()); heartbeat.on('error', finish); heartbeat.end(); return;
      }
      if (req.method === 'POST' && route === '/__rommayo/connect') {
        try {
          // Prevent a different website from posting credentials or changing this browser's session.
          if (!ownOrigin(req)) return json(res, 403, { error: 'Open this form directly in Rommayo.' });
          if (!req.headers['content-type']?.startsWith('application/json')) return json(res, 415, { error: 'Use the Rommayo connection form.' });
          let body = '';
          for await (const chunk of req) { body += chunk; if (body.length > 16000) { json(res, 413, { error: 'Request too large.' }); return; } }
          const command = JSON.parse(body);
          if (normalizeServer(command.server) !== upstream.href) return json(res, 400, { error: 'This web edition connects only to the server configured by its operator.' });
          if (command.consent !== true || (upstream.protocol === 'http:' && command.allowHttp !== true)) return json(res, 400, { error: 'Confirm the server and connection notices.' });
          const skin = `rommayo_theme=${command.steamTheme === false ? '0' : '1'}; Path=/; SameSite=Lax; HttpOnly; Max-Age=31536000`;
          const remember = command.rememberLogin !== false;
          const memory = `rommayo_remember=${remember ? '1' : '0'}; Path=/; SameSite=Lax; HttpOnly${remember ? '; Max-Age=31536000' : ''}`;
          if (command.webLogin) { res.setHeader('Set-Cookie', [skin, memory]); return json(res, 200, { redirect: '/' }); }
          if (typeof command.username !== 'string' || !command.username || command.username.includes(':') || command.username.length > 256 || typeof command.password !== 'string' || !command.password || command.password.length > 4096) return json(res, 400, { error: 'Enter your RomM username and password.' });
          const login = transport.request(new URL('/api/login', upstream), { method: 'POST', headers: { Authorization: `Basic ${Buffer.from(command.username + ':' + command.password).toString('base64')}`, 'Content-Length': '0' }, timeout: 20000 }, (response) => {
            response.resume();
            if (response.statusCode !== 200) return json(res, response.statusCode === 401 ? 401 : 502, { error: 'RomM sign-in failed. Check your credentials, or use RomM sign-in for SSO.' });
            res.setHeader('Set-Cookie', [...cookieHeaders(response.headers['set-cookie'], remember), skin, memory]); json(res, 200, { redirect: '/' });
          });
          login.on('timeout', () => login.destroy()); login.on('error', () => { if (!res.headersSent) json(res, 502, { error: 'Could not reach RomM. Check the upstream server.' }); }); login.end();
          command.password = '';
          return;
        } catch { return json(res, 400, { error: 'Invalid connection request.' }); }
      }
      if (req.method === 'GET' && route === '/__rommayo/manifest.webmanifest') {
        res.setHeader('Content-Type', 'application/manifest+json'); return res.end(JSON.stringify({ name: 'Rommayo', short_name: 'Rommayo', start_url: '/__rommayo/', display: 'standalone', background_color: '#171d25', theme_color: '#171d25', icons: [{ src: '/__rommayo/icon.png', sizes: '512x512', type: 'image/png', purpose: 'any' }] }));
      }
      const files = {
        '/__rommayo/': ['index.html', 'text/html'], '/__rommayo/index.html': ['index.html', 'text/html'],
        '/__rommayo/touch-controls.js': ['touch-controls.js', 'text/javascript'],
        '/__rommayo/session.js': ['session.js', 'text/javascript'], '/__rommayo/app.js': ['app.js', 'text/javascript'], '/__rommayo/style.css': ['style.css', 'text/css'],
        '/__rommayo/rommayo-theme.css': ['rommayo-theme.css', 'text/css'], '/__rommayo/web-bridge.js': ['web-bridge.js', 'text/javascript']
      };
      if (route === '/__rommayo/icon.png') { res.setHeader('Content-Type', 'image/png'); return fs.createReadStream(path.join(assets, 'icon.png')).pipe(res); }
      if (files[route] && req.method === 'GET') {
        const [name, type] = files[route]; res.setHeader('Content-Type', type);
        let content = fs.readFileSync(path.join(shared, name));
        if (name === 'index.html') content = content.toString().replace('<script src="app.js">', '<script src="web-bridge.js"></script><script src="app.js">');
        return res.end(content);
      }
      res.writeHead(404); return res.end('Not found');
    }
    const target = transport.request({ hostname: upstream.hostname, port: upstream.port || undefined, protocol: upstream.protocol, path: req.url, method: req.method, headers: headers(req) }, (response) => {
      const values = responseHeaders(response, req);
      if (response.headers['content-type']?.includes('text/html') && !route.startsWith('/api/') && !route.startsWith('/assets/')) {
        let body = Buffer.alloc(0);
        response.on('data', (chunk) => {
          body = Buffer.concat([body, chunk]);
          if (body.length > 4 * 1024 * 1024) { response.destroy(); if (!res.headersSent) { res.writeHead(502); res.end('Upstream HTML is too large.'); } }
        });
        response.on('end', () => {
          if (res.writableEnded) return;
          const useSkin = /(?:^|;\s*)rommayo_theme=1(?:;|$)/.test(req.headers.cookie || '');
          const insert = '<script src="/__rommayo/session.js" defer></script><link rel="manifest" href="/__rommayo/manifest.webmanifest">' + (useSkin ? '<link rel="stylesheet" href="/__rommayo/rommayo-theme.css">' : '');
          let html = body.toString('utf8').replace(/<link\b[^>]*rel=["']manifest["'][^>]*>/gi, '').replace('</head>', insert + '</head>');
          // Return a fresh response: upstream validators and lengths refer to the original HTML.
          delete values['content-length']; delete values['content-encoding']; delete values.etag; delete values['last-modified']; values['cache-control'] = 'no-store';
          res.writeHead(response.statusCode, values); res.end(html);
        });
      } else { res.writeHead(response.statusCode, values); response.pipe(res); }
    });
    target.on('error', () => { if (!res.headersSent) { res.writeHead(502, { 'Content-Type': 'text/plain' }); res.end('RomM is unavailable. Check the configured upstream server.'); } else res.destroy(); });
    req.on('aborted', () => target.destroy()); res.on('close', () => { if (!res.writableEnded) target.destroy(); }); req.pipe(target);
  });
  server.on('upgrade', (req, socket, head) => {
    const target = transport.request({ hostname: upstream.hostname, port: upstream.port || undefined, protocol: upstream.protocol, path: req.url, method: req.method, headers: headers(req) });
    target.on('upgrade', (response, upstreamSocket, upstreamHead) => {
      const lines = [`HTTP/1.1 ${response.statusCode} ${response.statusMessage}`];
      for (let i = 0; i < response.rawHeaders.length; i += 2) lines.push(`${response.rawHeaders[i]}: ${response.rawHeaders[i + 1]}`);
      socket.write(lines.join('\r\n') + '\r\n\r\n'); if (upstreamHead.length) socket.write(upstreamHead); if (head.length) upstreamSocket.write(head);
      socket.pipe(upstreamSocket).pipe(socket); socket.on('error', () => upstreamSocket.destroy()); upstreamSocket.on('error', () => socket.destroy());
    });
    target.on('response', (response) => { response.resume(); socket.end('HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n'); });
    target.on('error', () => socket.destroy()); target.end();
  });
  return server;
}
if (require.main === module) {
  if (!process.env.ROMMAYO_UPSTREAM) { console.error('Set ROMMAYO_UPSTREAM to your RomM URL. See docs/web.md.'); process.exitCode = 1; }
  else {
    const server = createServer({ upstream: process.env.ROMMAYO_UPSTREAM });
    server.listen(Number(process.env.PORT || 8787), process.env.HOST || '127.0.0.1', () => console.log('Rommayo web is ready. Open /__rommayo/ on the configured host and port.'));
  }
}
module.exports = { createServer };
