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
  pngBegin: (path, width, height) => ipcRenderer.invoke("png:begin", { path, width, height }),
  pngRows: (path, bytes, rows) => ipcRenderer.invoke("png:rows", { path, bytes, rows }),
  pngEnd: (path) => ipcRenderer.invoke("png:end", { path }),
  pngAbort: (path) => ipcRenderer.invoke("png:abort", { path }),
  reportMenuSummary: (summary) => ipcRenderer.send("menu:summary", summary),
  onMenuAction: (handler) => {
    const listener = (_event, action) => handler(action);
    ipcRenderer.on("menu-action", listener);
    return () => ipcRenderer.removeListener("menu-action", listener);
  },
});
