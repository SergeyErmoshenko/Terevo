import type {AppStateDto} from "./dto";
import type {DispatchableAction, MenuAction} from "./actions";

export interface Hello {
    projectsRoot: string;
    previewDirectory: string;
}

export interface BackendResponse {
    id: number;
    state: AppStateDto | null;
    error: string | null;
    hello: Hello | null;
}

export interface MenuSummary {
    isProjectOpen: boolean;
    canUndo: boolean;
    canRedo: boolean;
    undoLabel: string;
    redoLabel: string;
    canGoBack: boolean;
    canGoForward: boolean;
    themeMode: AppStateDto["themeMode"];
    hasSelection: boolean;
}

export interface TerevoBridge {
    start(): Promise<BackendResponse>;

    // requestId and action travel as separate fields so an action's own "id" property (e.g.
    // editPerson's target person) never collides with the request envelope's id.
    dispatch(requestId: number, action: DispatchableAction): Promise<BackendResponse | null>;

    info(): Promise<{ platform: string; home: string }>;

    pathForFile(file: File): string | null;

    choosePngPath(): Promise<string | null>;

    pngBegin(path: string, width: number, height: number): Promise<boolean>;

    pngRows(path: string, bytes: Uint8Array, rows: number): Promise<boolean>;

    pngEnd(path: string): Promise<boolean>;

    pngAbort(path: string): Promise<boolean>;

    reportMenuSummary(summary: MenuSummary): void;

    onMenuAction(handler: (action: MenuAction) => void): () => void;
}

declare global {
    interface Window {
        terevo?: TerevoBridge;
    }
}

export function getBridge(): TerevoBridge | null {
    return window.terevo ?? null;
}

// Local files (photos, thumbnails, rendered PDF previews) are served through a privileged custom
// scheme registered in electron/main.cjs, which only answers for paths under the current
// project's directory or the shared preview directory - never arbitrary filesystem paths.
// Windows paths ("C:\...") are normalized to forward slashes and given a leading slash so the
// whole thing parses as a URL pathname; main.cjs strips that leading slash back off before a
// drive letter.
export function fileUrl(path: string | null | undefined): string | undefined {
    if (!path) return undefined;
    const normalized = path.replace(/\\/g, "/");
    const withLeadingSlash = normalized.startsWith("/") ? normalized : `/${normalized}`;
    return `terevo-file://local${encodeURI(withLeadingSlash)}`;
}
