// Mirrors me/terevo/server/Actions.kt one for one. Each variant's "type" is the @SerialName.
import type {
  EventFormFieldsDto,
  LayoutDirection,
  LayoutDensity,
  LayoutMode,
  MainTab,
  PersonFormFieldsDto,
  RelationFieldsDto,
  RelationMode,
  SearchFilterDto,
  ThemeMode,
} from "./dto";

export type Action =
  | { type: "createProject"; name: string }
  | { type: "openProject"; path: string }
  | { type: "createProjectFromGedcom"; name: string; gedcomPath: string }
  | { type: "exportGedcom"; path: string }
  | { type: "reportPngExport"; succeeded: boolean }
  | { type: "confirmGedcomImport" }
  | { type: "cancelGedcomImport" }
  | { type: "undo" }
  | { type: "redo" }
  | { type: "navigateBack" }
  | { type: "navigateForward" }
  | { type: "changeLayoutMode"; mode: LayoutMode }
  | { type: "changeLayoutDepth"; depth: number | null }
  | { type: "changeLayoutDirection"; direction: LayoutDirection }
  | { type: "changeLayoutDensity"; density: LayoutDensity }
  | { type: "changeSearchFilter"; filter: SearchFilterDto }
  | { type: "addPerson" }
  | { type: "editPerson"; id?: string | null }
  | { type: "deletePerson"; id?: string | null }
  | { type: "addRelative"; mode: RelationMode; id?: string | null }
  | { type: "addMedia"; paths: string[] }
  | { type: "openMedia"; id: string }
  | { type: "removeMedia"; id: string }
  | { type: "changeMediaZoom"; zoom: number }
  | { type: "changeMediaPage"; delta: number }
  | { type: "closeMedia" }
  | { type: "selectPerson"; id: string | null }
  | { type: "selectOnCanvas"; id: string | null }
  | { type: "selectSearchResult"; id: string }
  | { type: "showOnTree"; id: string }
  | { type: "clearHighlight" }
  | { type: "viewPerson"; id: string }
  | { type: "closePersonView" }
  | { type: "updatePersonForm"; fields: PersonFormFieldsDto }
  | { type: "addPersonFormMedia"; paths: string[] }
  | { type: "removePendingPersonMedia"; path: string }
  | { type: "savePerson" }
  | { type: "cancelPerson" }
  | { type: "confirmDiscardPerson" }
  | { type: "keepEditingPerson" }
  | { type: "updateRelationDialog"; fields: RelationFieldsDto }
  | { type: "createRelative" }
  | { type: "saveRelation" }
  | { type: "cancelRelation" }
  | { type: "startDrag"; source: string }
  | { type: "dropDrag"; target: string | null; x: number; y: number }
  | { type: "cancelDrag" }
  | { type: "chooseDragRelationMode"; mode: RelationMode }
  | { type: "cancelDragRelationMenu" }
  | { type: "openKinshipDialog" }
  | { type: "updateKinshipDialog"; query: string; target: string | null }
  | { type: "closeKinshipDialog" }
  | { type: "openStatistics" }
  | { type: "closeStatistics" }
  | { type: "changeThemeMode"; mode: ThemeMode }
  | { type: "changeMainTab"; tab: MainTab }
  | { type: "toggleSidebar" }
  | { type: "addEvent" }
  | { type: "editEvent"; id: string }
  | { type: "deleteEvent"; id: string }
  | { type: "updateEventForm"; fields: EventFormFieldsDto }
  | { type: "saveEvent" }
  | { type: "cancelEvent" };

// Electron-side-only pseudo actions: intercepted in main.cjs before (or instead of) reaching the
// backend, because they need a native file dialog first. Kept in the same union so components
// dispatch them identically; see electron/main.cjs's ipcMain.handle("backend:call", ...).
export type DialogAction =
  | { type: "openProjectDialog" }
  | { type: "importGedcomDialog"; name: string }
  | { type: "exportGedcomDialog" }
  | { type: "exportPngDialog" }
  | { type: "chooseMediaDialog"; kind?: MediaPickKind }
  | { type: "choosePersonFormMediaDialog"; kind?: MediaPickKind };

// Which file filter the native picker opens with: photos or documents (PDF, Word, ...).
export type MediaPickKind = "photo" | "document";

export type DispatchableAction = Action | DialogAction;

// Fired only from the native application menu, for the two items with no equivalent dispatchable
// action: prompting for a new project's name needs the renderer's own name dialog first.
export type MenuOnlyAction = { type: "promptNewProject" } | { type: "promptImportGedcom" };

export type MenuAction = DispatchableAction | MenuOnlyAction;
