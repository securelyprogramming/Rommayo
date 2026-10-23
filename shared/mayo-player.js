(() => {
  'use strict';
  const brand = window.rommayoPlayer ? 'rommayo' : 'emulatormayo';
  const bridge = window[brand + 'Player'];
  if (!bridge || window.__mayoPlayerShell) return;
  window.__mayoPlayerShell = true;
  const key = brand + '-touch-controls';
  let visible = null, unloading = false;
  try { const saved = localStorage.getItem(key); if (saved === 'hidden' || saved === 'shown') visible = saved === 'shown'; } catch (_) {}
  // Upstream also emits exit during beforeunload. Let normal navigation continue.
  window.addEventListener('beforeunload', () => { unloading = true; setTimeout(() => { unloading = false; }, 0); }, {capture:true});
  const hooked = new WeakSet(), attached = new WeakSet();
  const style = document.createElement('style');
  const accent = brand === 'rommayo' ? '#67c4ff' : '#67e8a5';
  const background = brand === 'rommayo' ? '#142435ed' : '#102018ed';
  style.textContent = `
    .mayo-player-shell > .ejs_virtualGamepad_open { opacity:0.8 !important; }
    .mayo-player-shell.ejs_big_screen > .ejs_menu_bar { opacity:1 !important;visibility:visible !important;pointer-events:auto !important;transform:translateY(0) !important;overflow-x:auto;max-width:100%; }
    .mayo-touch-hidden > .ejs_virtualGamepad_parent { display:none !important; }
    .mayo-touch-toggle { position:absolute;top:max(8px,env(safe-area-inset-top));left:max(8px,env(safe-area-inset-left));z-index:20;width:auto;max-width:calc(100% - 16px);min-height:44px;min-width:44px;padding:8px 12px;border:1px solid ${accent};border-radius:7px;background:${background};color:#ecf8f0;font:600 13px system-ui,sans-serif;cursor:pointer;touch-action:manipulation; }
    .mayo-touch-toggle:focus-visible { outline:3px solid ${accent};outline-offset:2px; }
  `;
  (document.head || document.documentElement).appendChild(style);
  function attach(engine) {
    const parent = engine.elements?.parent;
    if (!parent || !engine.virtualGamepad || typeof engine.toggleVirtualGamepad !== 'function' || attached.has(engine)) return;
    attached.add(engine);
    parent.classList.add('mayo-player-shell');
    if (visible === null) visible = getComputedStyle(engine.virtualGamepad).display !== 'none';
    const button = document.createElement('button');
    button.type = 'button'; button.className = 'mayo-touch-toggle';
    function apply() {
      parent.classList.toggle('mayo-touch-hidden', !visible);
      engine.toggleVirtualGamepad(visible);
      button.textContent = visible ? 'Hide touch controls' : 'Show touch controls';
      button.setAttribute('aria-label', button.textContent);
      button.setAttribute('aria-pressed', String(visible));
    }
    button.addEventListener('click', () => {
      visible = parent.classList.contains('mayo-touch-hidden') || getComputedStyle(engine.virtualGamepad).display === 'none';
      try { localStorage.setItem(key, visible ? 'shown' : 'hidden'); } catch (_) {}
      apply(); parent.focus();
    });
    parent.appendChild(button); apply();
  }
  function scan() {
    const engine = window.EJS_emulator;
    if (!engine || typeof engine.on !== 'function') return;
    if (!hooked.has(engine)) {
      hooked.add(engine);
      let exited = false;
      engine.on('exit', () => { if (!unloading && !exited) { exited = true; bridge.postMessage('exit'); } });
      engine.on('start', () => attach(engine));
    }
    if (engine.started) attach(engine);
  }
  scan();
  // RomM creates/replaces its player after SPA navigation; retain its own hooks.
  const timer = setInterval(scan, 300);
  window.addEventListener('pagehide', () => clearInterval(timer), {once:true});
})();
