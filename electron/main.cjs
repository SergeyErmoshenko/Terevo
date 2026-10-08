const { app, BrowserWindow, dialog, ipcMain, Menu, protocol, net } = require("electron");
const { spawn } = require("node:child_process");
const path = require("node:path");
const fs = require("node:fs");
const os = require("node:os");
const zlib = require("node:zlib");
const { pathToFileURL } = require("node:url");

let backend;
let backendAddress;
let backendToken;
let backendLog = "";
let mainWindow;

// Updated from every backend response (projectDirectory) and the initial /hello call
// (previewDirectory). The terevo-file:// handler only ever serves files under these two roots.
let currentProjectDirectory = null;
let previewDirectory = null;

// Set only while a PNG save dialog's result is pending use, and cleared the moment it is
// consumed - a one-shot grant, not a standing permission, so the renderer cannot write anywhere
// else just by knowing a path string.
let grantedPngPath = null;
let pngStream = null;

let menuSummary = {
  isProjectOpen: false,
  canUndo: false,
  canRedo: false,
  undoLabel: "Отменить",
  redoLabel: "Повторить",
  canGoBack: false,
  canGoForward: false,
  themeMode: "SYSTEM",
  hasSelection: false,
};

function backendExecutable() {
  const binary = process.platform === "win32" ? "terevo-backend.bat" : "terevo-backend";
  if (app.isPackaged) return path.join(process.resourcesPath, "backend", "bin", binary);
  return path.join(__dirname, "..", "build", "install", "terevo-backend", "bin", binary);
}

function packagedBackendEnv() {
  const backendHome = app.isPackaged
    ? path.join(process.resourcesPath, "backend")
    : path.join(__dirname, "..", "build", "install", "terevo-backend");
  const dot = process.platform === "win32" ? path.join(backendHome, "graphviz", "windows", "bin", "dot.exe") : "dot";
  return {
    backendHome,
    graphvizPath: fs.existsSync(dot) ? dot : null,
  };
}

function startBackend() {
  return new Promise((resolve, reject) => {
    const executable = backendExecutable();
    if (!fs.existsSync(executable)) {
      reject(new Error(`Backend is not built: ${executable}. Run ./gradlew installDist first.`));
      return;
    }
    const backendEnv = packagedBackendEnv();
    backend = spawn(executable, [], {
      cwd: app.getPath("userData"),
      windowsHide: true,
      stdio: ["ignore", "ignore", "pipe"],
      env: {
        ...process.env,
        ...(backendEnv.graphvizPath ? { TEREVO_DOT: backendEnv.graphvizPath } : {}),
      },
    });
    const timeout = setTimeout(() => reject(new Error(`Backend did not start. ${backendLog}`)), 20000);
    backend.stderr.setEncoding("utf8");
    backend.stderr.on("data", (chunk) => {
      backendLog = (backendLog + chunk).slice(-12000);
      process.stderr.write(chunk);
      const marker = backendLog.match(/TEREVO_SERVER_READY (\d+) ([a-f0-9-]+)/);
      if (!marker || backendAddress) return;
      backendAddress = `http://127.0.0.1:${marker[1]}`;
      backendToken = marker[2];
      clearTimeout(timeout);
      resolve();
    });
    backend.once("error", (error) => {
      clearTimeout(timeout);
      reject(error);
    });
    backend.once("exit", (code) => {
      if (!backendAddress) {
        clearTimeout(timeout);
        reject(new Error(`Backend exited (${code}). ${backendLog}`));
      }
      backendAddress = null;
      backendToken = null;
    });
  });
}

async function waitForBackend() {
  if (!backendAddress) throw new Error("Backend is not available");
  const started = Date.now();
  let lastError;
  while (Date.now() - started < 5000) {
    try {
      const response = await fetch(`${backendAddress}/health`);
      if (response.ok) return;
    } catch (error) {
      lastError = error;
    }
    await new Promise((resolve) => setTimeout(resolve, 120));
  }
  throw lastError ?? new Error("Backend health check failed");
}

async function backendGet(route) {
  await waitForBackend();
  const response = await fetch(`${backendAddress}${route}`, { headers: { authorization: `Bearer ${backendToken}` } });
  if (!response.ok) throw new Error(`Backend returned HTTP ${response.status}`);
  return response.json();
}

async function callBackendOnce(request) {
  const response = await fetch(`${backendAddress}/api`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${backendToken}`,
    },
    body: JSON.stringify(request),
  });
  if (!response.ok) throw new Error(`Backend returned HTTP ${response.status}`);
  return response.json();
}

function rememberProjectDirectory(response) {
  const directory = response?.state?.projectDirectory;
  if (directory !== undefined) currentProjectDirectory = directory;
  return response;
}

async function callBackend(request) {
  if (!backendAddress || !backendToken) throw new Error("Backend is not available");
  await waitForBackend();
  let lastError;
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      return rememberProjectDirectory(await callBackendOnce(request));
    } catch (error) {
      lastError = error;
      await new Promise((resolve) => setTimeout(resolve, 120));
    }
  }
  throw new Error(`${lastError?.message || "Backend request failed"}. ${backendLog.slice(-2000)}`);
}

// The request envelope uses "requestId", not "id", specifically so an action's own "id" field
// (e.g. editPerson's target person, selectPerson's target) never collides with the envelope's.
ipcMain.handle("backend:call", async (_event, { requestId, action }) => {
  if (!action || typeof action.type !== "string") throw new Error("Invalid action");
  if (action.type === "openProjectDialog") {
    const result = await dialog.showOpenDialog({
      title: "Открыть проект Terevo",
      properties: ["openFile"],
      filters: [{ name: "Проекты Terevo", extensions: ["terevo"] }],
    });
    if (result.canceled) return null;
    return callBackend({ id: requestId, action: { type: "openProject", path: result.filePaths[0] } });
  }
  if (action.type === "importGedcomDialog") {
    const result = await dialog.showOpenDialog({
      title: "Импорт GEDCOM",
      properties: ["openFile"],
      filters: [{ name: "GEDCOM", extensions: ["ged", "gedcom"] }],
    });
    if (result.canceled || !result.filePaths[0]) return null;
    return callBackend({
      id: requestId,
      action: { type: "createProjectFromGedcom", name: action.name, gedcomPath: result.filePaths[0] },
    });
  }
  if (action.type === "exportGedcomDialog") {
    const result = await dialog.showSaveDialog({
      title: "Экспорт GEDCOM",
      defaultPath: "family.ged",
      filters: [{ name: "GEDCOM", extensions: ["ged"] }],
    });
    if (result.canceled || !result.filePath) return null;
    return callBackend({ id: requestId, action: { type: "exportGedcom", path: result.filePath } });
  }
  if (action.type === "chooseMediaDialog" || action.type === "choosePersonFormMediaDialog") {
    const imageExtensions = ["jpg", "jpeg", "png", "gif", "bmp", "webp"];
    const documentExtensions = ["pdf", "doc", "docx", "odt", "rtf", "txt"];
    const photoFilter = { name: "Изображения", extensions: imageExtensions };
    const documentFilter = { name: "Документы (PDF, Word)", extensions: documentExtensions };
    const filters =
      action.kind === "document"
        ? [documentFilter, photoFilter]
        : action.kind === "photo"
          ? [photoFilter, documentFilter]
          : [{ name: "Фото и документы", extensions: [...imageExtensions, ...documentExtensions] }, photoFilter, documentFilter];
    const result = await dialog.showOpenDialog({
      title: action.kind === "document" ? "Добавить документ" : action.kind === "photo" ? "Добавить фото" : "Добавить фото или документ",
      properties: ["openFile", "multiSelections"],
      filters: [...filters, { name: "Все файлы", extensions: ["*"] }],
    });
    if (result.canceled || result.filePaths.length === 0) return null;
    const paths = result.filePaths;
    return callBackend({
      id: requestId,
      action: action.type === "chooseMediaDialog" ? { type: "addMedia", paths } : { type: "addPersonFormMedia", paths },
    });
  }
  return callBackend({ id: requestId, action });
});

ipcMain.handle("backend:start", async () => {
  const hello = await backendGet("/hello");
  if (hello?.hello?.previewDirectory) previewDirectory = hello.hello.previewDirectory;
  return rememberProjectDirectory(hello);
});

ipcMain.handle("backend:info", () => ({ platform: process.platform, home: os.homedir() }));

ipcMain.handle("dialog:choosePngPath", async () => {
  const result = await dialog.showSaveDialog({
    title: "Экспорт в PNG",
    defaultPath: "family-tree.png",
    filters: [{ name: "PNG", extensions: ["png"] }],
  });
  if (result.canceled || !result.filePath) return null;
  grantedPngPath = result.filePath;
  return grantedPngPath;
});

// A PNG too large for one canvas is streamed in: the renderer paints it in strips of rows and the
// main process deflates them straight into the file, so the whole picture is never in memory. Only
// the path granted by the save dialog can be written, once.
function pngChunk(type, data) {
  const typeBuffer = Buffer.from(type, "ascii");
  const header = Buffer.alloc(4);
  header.writeUInt32BE(data.length);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(zlib.crc32(Buffer.concat([typeBuffer, data])) >>> 0);
  return Buffer.concat([header, typeBuffer, data, crc]);
}

function abortPng() {
  const stream = pngStream;
  pngStream = null;
  if (!stream) return;
  stream.deflate.destroy();
  stream.file.destroy();
  fs.promises.unlink(stream.path).catch(() => {});
}

ipcMain.handle("png:begin", async (_event, { path: targetPath, width, height }) => {
  if (!grantedPngPath || targetPath !== grantedPngPath) return false;
  grantedPngPath = null;
  if (!Number.isInteger(width) || !Number.isInteger(height) || width < 1 || height < 1 || width > 100000 || height > 100000) return false;
  abortPng();
  try {
    const file = fs.createWriteStream(targetPath);
    const deflate = zlib.createDeflate({ level: 6 });
    const stream = { path: targetPath, file, deflate, width, height, rows: 0, failure: null };
    file.on("error", (error) => (stream.failure = error));
    deflate.on("error", (error) => (stream.failure = error));
    deflate.on("data", (chunk) => {
      if (!file.write(pngChunk("IDAT", chunk))) {
        deflate.pause();
        file.once("drain", () => deflate.resume());
      }
    });
    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(width, 0);
    ihdr.writeUInt32BE(height, 4);
    ihdr[8] = 8; // bit depth
    ihdr[9] = 2; // truecolor RGB
    file.write(Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), pngChunk("IHDR", ihdr)]));
    pngStream = stream;
    return true;
  } catch (error) {
    process.stderr.write(`PNG export failed: ${error.message}\n`);
    return false;
  }
});

ipcMain.handle("png:rows", async (_event, { path: targetPath, bytes, rows }) => {
  const stream = pngStream;
  if (!stream || stream.path !== targetPath || stream.failure) return false;
  const rowBytes = stream.width * 3;
  if (!Number.isInteger(rows) || rows < 1 || stream.rows + rows > stream.height || bytes.length !== rowBytes * rows) return false;
  const data = Buffer.alloc((rowBytes + 1) * rows); // every row starts with filter type 0 (none)
  for (let row = 0; row < rows; row++) {
    Buffer.from(bytes.buffer, bytes.byteOffset + row * rowBytes, rowBytes).copy(data, row * (rowBytes + 1) + 1);
  }
  stream.rows += rows;
  if (!stream.deflate.write(data)) await new Promise((resolve) => stream.deflate.once("drain", resolve));
  return !stream.failure;
});

ipcMain.handle("png:end", async (_event, { path: targetPath }) => {
  const stream = pngStream;
  if (!stream || stream.path !== targetPath) return false;
  if (stream.failure || stream.rows !== stream.height) {
    abortPng();
    return false;
  }
  try {
    await new Promise((resolve, reject) => {
      stream.deflate.once("end", resolve);
      stream.deflate.once("error", reject);
      stream.deflate.end();
      stream.deflate.resume();
    });
    await new Promise((resolve, reject) => {
      stream.file.once("finish", resolve);
      stream.file.once("error", reject);
      stream.file.end(pngChunk("IEND", Buffer.alloc(0)));
    });
    pngStream = null;
    return true;
  } catch (error) {
    process.stderr.write(`PNG export failed: ${error.message}\n`);
    abortPng();
    return false;
  }
});

ipcMain.handle("png:abort", (_event, { path: targetPath }) => {
  if (pngStream && pngStream.path === targetPath) abortPng();
  return true;
});

ipcMain.on("menu:summary", (_event, summary) => {
  menuSummary = summary;
  buildMenu();
});

function sendMenuAction(action) {
  mainWindow?.webContents.send("menu-action", action);
}

function buildMenu() {
  const s = menuSummary;
  const template = [
    ...(process.platform === "darwin" ? [{ label: app.name, role: "appMenu" }] : []),
    {
      label: "Файл",
      submenu: [
        { label: "Создать проект…", accelerator: "CmdOrCtrl+N", click: () => sendMenuAction({ type: "promptNewProject" }) },
        { label: "Открыть проект…", accelerator: "CmdOrCtrl+O", click: () => sendMenuAction({ type: "openProjectDialog" }) },
        { label: "Импорт GEDCOM…", click: () => sendMenuAction({ type: "promptImportGedcom" }) },
        { type: "separator" },
        { label: "Экспорт GEDCOM…", enabled: s.isProjectOpen, click: () => sendMenuAction({ type: "exportGedcomDialog" }) },
        { label: "Экспорт в PNG…", enabled: s.isProjectOpen, click: () => sendMenuAction({ type: "exportPngDialog" }) },
        { type: "separator" },
        { role: "close" },
      ],
    },
    {
      label: "Правка",
      submenu: [
        { label: s.undoLabel, accelerator: "CmdOrCtrl+Z", enabled: s.canUndo, click: () => sendMenuAction({ type: "undo" }) },
        { label: s.redoLabel, accelerator: "CmdOrCtrl+Shift+Z", enabled: s.canRedo, click: () => sendMenuAction({ type: "redo" }) },
      ],
    },
    {
      label: "Вид",
      submenu: [
        { label: "Назад", accelerator: "Alt+Left", enabled: s.canGoBack, click: () => sendMenuAction({ type: "navigateBack" }) },
        { label: "Вперёд", accelerator: "Alt+Right", enabled: s.canGoForward, click: () => sendMenuAction({ type: "navigateForward" }) },
        { type: "separator" },
        { label: "Статистика", enabled: s.isProjectOpen, click: () => sendMenuAction({ type: "openStatistics" }) },
        { type: "separator" },
        {
          label: "Тема",
          submenu: [
            { label: "Светлая", type: "radio", checked: s.themeMode === "LIGHT", click: () => sendMenuAction({ type: "changeThemeMode", mode: "LIGHT" }) },
            { label: "Тёмная", type: "radio", checked: s.themeMode === "DARK", click: () => sendMenuAction({ type: "changeThemeMode", mode: "DARK" }) },
            { label: "Системная", type: "radio", checked: s.themeMode === "SYSTEM", click: () => sendMenuAction({ type: "changeThemeMode", mode: "SYSTEM" }) },
          ],
        },
        { type: "separator" },
        { role: "toggleDevTools" },
      ],
    },
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

// Serves local files (photos, thumbnails, rendered PDF pages) to the renderer without handing it
// raw filesystem access: only paths inside the currently open project's directory or the shared
// preview directory are ever answered, and everything else gets a 403.
function registerFileProtocol() {
  protocol.handle("terevo-file", (request) => {
    const url = new URL(request.url);
    let requested = decodeURIComponent(url.pathname);
    if (process.platform === "win32" && /^\/[A-Za-z]:/.test(requested)) requested = requested.slice(1);
    const resolved = path.resolve(requested);
    const allowedRoots = [currentProjectDirectory, previewDirectory].filter(Boolean).map((root) => path.resolve(root));
    const allowed = allowedRoots.some((root) => resolved === root || resolved.startsWith(root + path.sep));
    if (!allowed) return new Response("Forbidden", { status: 403 });
    // The page itself is loaded from file://, so images from this scheme are cross-origin; without
    // CORS headers the PNG export could not read them back without tainting its canvas.
    return net.fetch(pathToFileURL(resolved).toString()).then((response) => {
      const headers = new Headers(response.headers);
      headers.set("access-control-allow-origin", "*");
      return new Response(response.body, { status: response.status, headers });
    });
  });
}

async function createWindow() {
  await startBackend();
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 900,
    minHeight: 600,
    backgroundColor: "#1c1d21",
    title: "Terevo",
    icon: path.join(__dirname, "assets", "icon.png"),
    webPreferences: {
      preload: path.join(__dirname, "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  mainWindow.on("closed", () => {
    mainWindow = null;
  });
  if (!app.isPackaged && process.env.TEREVO_DEV_SERVER_URL) {
    await mainWindow.loadURL(process.env.TEREVO_DEV_SERVER_URL);
  } else {
    await mainWindow.loadFile(path.join(__dirname, "..", "dist", "index.html"));
  }
}

protocol.registerSchemesAsPrivileged([
  { scheme: "terevo-file", privileges: { standard: false, secure: true, supportFetchAPI: true, stream: true, corsEnabled: true } },
]);

app.whenReady().then(async () => {
  // In development Electron runs as its own stock bundle, so the Dock still shows the Electron
  // atom until it is replaced here. The packaged app gets the icon from electron-builder.
  if (process.platform === "darwin" && app.dock) app.dock.setIcon(path.join(__dirname, "assets", "icon.png"));
  registerFileProtocol();
  buildMenu();
  await createWindow();
}).catch((error) => {
  dialog.showErrorBox("Не удалось запустить Terevo", error.message);
  app.quit();
});
app.on("window-all-closed", () => {
  if (backend && !backend.killed) backend.kill();
  if (process.platform !== "darwin") app.quit();
});
app.on("before-quit", () => {
  if (backend && !backend.killed) backend.kill();
});
app.on("activate", () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow();
});
