'use strict';
const { contextBridge, ipcRenderer } = require('electron');
// Only navigation after an EmulatorJS exit; no shell, files, credentials or general IPC.
contextBridge.exposeInMainWorld('rommayoPlayer', {
  postMessage: message => { if (message === 'exit') ipcRenderer.send('rommayo:player-exit'); }
});
