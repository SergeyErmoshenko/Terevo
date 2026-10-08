import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const read = (relative) => fs.readFileSync(path.join(root, relative), "utf8");

test("desktop shell enables process isolation and disables Node in renderer", () => {
  const main = read("electron/main.cjs");
  assert.match(main, /contextIsolation:\s*true/);
  assert.match(main, /nodeIntegration:\s*false/);
  assert.match(main, /sandbox:\s*true/);
});

test("preload exposes only the backend API and no direct filesystem access", () => {
  const preload = read("electron/preload.cjs");
  assert.match(preload, /contextBridge\.exposeInMainWorld\("terevo"/);
  assert.doesNotMatch(preload, /require\("node:fs"\)|require\("fs"\)/);
});

test("preload's dispatch envelope keeps requestId separate from the action", () => {
  // editPerson/deletePerson/selectPerson all carry their own "id" field; merging a request
  // counter into the same object would silently overwrite it. See app/client.ts.
  const preload = read("electron/preload.cjs");
  assert.match(preload, /dispatch:\s*\(requestId,\s*action\)\s*=>\s*ipcRenderer\.invoke\("backend:call",\s*\{\s*requestId,\s*action\s*\}\)/);
  const main = read("electron/main.cjs");
  assert.match(main, /\{\s*requestId,\s*action\s*\}/);
  assert.doesNotMatch(main, /const\s*\{\s*type,\s*id,\s*\.\.\.fields\s*\}/);
});

test("frontend source uses Russian document language and the new TypeScript entry point", () => {
  const html = read("index.html");
  assert.match(html, /lang="ru"/);
  assert.match(html, /main\.tsx/);
  const app = read("frontend/components/App.tsx");
  assert.match(app, /Импорт \.ged/);
  assert.match(app, /Новый проект/);
  assert.doesNotMatch(app, /node:fs|from ["']fs["']/);
});

test("the tree canvas carries its accessible label", () => {
  const canvas = read("frontend/components/tree/TreeCanvas.tsx");
  assert.match(canvas, /aria-label="Семейное древо"/);
});

test("desktop shell loads the built frontend unless dev server is explicit", () => {
  const main = read("electron/main.cjs");
  assert.match(main, /process\.env\.TEREVO_DEV_SERVER_URL/);
  assert.match(main, /loadFile\(path\.join\(__dirname, "\.\.", "dist", "index\.html"\)\)/);
  assert.doesNotMatch(main, /loadURL\("http:\/\/localhost:5173"\)/);
});

test("desktop shell bridges GEDCOM import through a native file dialog", () => {
  const main = read("electron/main.cjs");
  assert.match(main, /action\.type === "importGedcomDialog"/);
  assert.match(main, /extensions: \["ged", "gedcom"\]/);
  assert.match(main, /type: "createProjectFromGedcom"/);
});

test("desktop shell waits for backend health before API calls", () => {
  const main = read("electron/main.cjs");
  assert.match(main, /async function waitForBackend\(\)/);
  assert.match(main, /`\$\{backendAddress\}\/health`/);
  assert.match(main, /await waitForBackend\(\)/);
});

test("local files are served through a privileged scheme scoped to the project and preview directories", () => {
  const main = read("electron/main.cjs");
  assert.match(main, /registerSchemesAsPrivileged/);
  assert.match(main, /protocol\.handle\("terevo-file"/);
  assert.match(main, /currentProjectDirectory/);
  assert.match(main, /previewDirectory/);
  assert.match(main, /return new Response\("Forbidden", \{ status: 403 \}\)/);
});

test("PNG export writes only to the path just granted by a save dialog", () => {
  const main = read("electron/main.cjs");
  assert.match(main, /ipcMain\.handle\("dialog:choosePngPath"/);
  assert.match(main, /ipcMain\.handle\("png:begin"/);
  assert.match(main, /ipcMain\.handle\("png:rows"/);
  assert.match(main, /ipcMain\.handle\("png:end"/);
  assert.match(main, /if \(!grantedPngPath \|\| targetPath !== grantedPngPath\) return false;/);
});

test("the native menu mirrors undo/redo state sent from the renderer", () => {
  const main = read("electron/main.cjs");
  assert.match(main, /ipcMain\.on\("menu:summary"/);
  assert.match(main, /label: s\.undoLabel/);
  assert.match(main, /enabled: s\.canUndo/);
  assert.doesNotMatch(main, /role: "reload"/);
});
