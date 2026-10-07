const { contextBridge, ipcRenderer, webUtils } = require("electron");

contextBridge.exposeInMainWorld("terevo", {
  start: () => ipcRenderer.invoke("backend:start"),
  dispatch: (requestId, action) => ipcRenderer.invoke("backend:call", { requestId, action }),
  info: () => ipcRenderer.invoke("backend:info"),
  pathForFile: (file) => {
    try {
      return webUtils.getPathForFile(file) || null;
    } catch {
      return null;
    }
  },
  choosePngPath: () => ipcRenderer.invoke("dialog:choosePngPath"),
  writePng: (path, bytes) => ipcRenderer.invoke("fs:writePng", { path, bytes }),
  reportMenuSummary: (summary) => ipcRenderer.send("menu:summary", summary),
  onMenuAction: (handler) => {
    const listener = (_event, action) => handler(action);
    ipcRenderer.on("menu-action", listener);
    return () => ipcRenderer.removeListener("menu-action", listener);
  },
});
