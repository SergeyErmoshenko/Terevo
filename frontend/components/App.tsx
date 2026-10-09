import {useEffect, useRef, useState} from "react";
import type {DispatchableAction} from "../app/actions";
import {useBackend} from "../app/useBackend";
import {useEffectiveTheme} from "../app/theme";
import {getBridge} from "../app/client";
import {Icon} from "./icons/Icon";
import {TopBar} from "./shell/TopBar";
import {NavRail} from "./shell/NavRail";
import {SearchBar} from "./shell/SearchBar";
import {TableSearch} from "./shell/TableSearch";
import {LayoutControls} from "./shell/LayoutControls";
import {TreeCanvas} from "./tree/TreeCanvas";
import {exportTreePng} from "./tree/exportPng";
import {DragRelationMenuPopup} from "./tree/DragRelationMenuPopup";
import {Inspector} from "./inspector/Inspector";
import {PeopleTable} from "./tables/PeopleTable";
import {EventsTable} from "./tables/EventsTable";
import {DocumentsScreen} from "./tables/DocumentsScreen";
import {PersonFormDialog} from "./dialogs/PersonFormDialog";
import {PersonViewDialog} from "./dialogs/PersonViewDialog";
import {RelationDialog} from "./dialogs/RelationDialog";
import {EventFormDialog} from "./dialogs/EventFormDialog";
import {GedcomPreviewDialog} from "./dialogs/GedcomPreviewDialog";
import {DiscardDialog} from "./dialogs/DiscardDialog";
import {ProjectNameDialog} from "./dialogs/ProjectNameDialog";
import {StatisticsDialog} from "./dialogs/StatisticsDialog";
import {KinshipDialog} from "./dialogs/KinshipDialog";
import {MediaViewerDialog} from "./dialogs/MediaViewerDialog";

type ProjectDialogKind = "create" | "gedcom" | null;

const STATUS_TOAST_MS = 1000;

function App() {
    const {state, dispatch, busy, error, dismissError} = useBackend();
    // The theme class is applied to <html> (see theme.ts) so CSS custom properties resolve
    // correctly before body's own styles do; the hook is still called here to keep the effect
    // (and its matchMedia listener) alive for the lifetime of the app shell.
    useEffectiveTheme(state.themeMode);
    const [projectDialog, setProjectDialog] = useState<ProjectDialogKind>(null);
    // Tables (people, events) have their own local search; it is dropped when the tab changes.
    const [tableQuery, setTableQuery] = useState("");
    useEffect(() => setTableQuery(""), [state.mainTab]);

    // Escape closes whatever is on top, in the same priority order the old desktop UI used;
    // Alt+Left/Right mirror the back/forward toolbar buttons.
    useEffect(() => {
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                if (state.personForm) void dispatch({type: "cancelPerson"});
                else if (state.eventForm) void dispatch({type: "cancelEvent"});
                else if (state.relationDialog) void dispatch({type: "cancelRelation"});
                else if (state.personViewOpen) void dispatch({type: "closePersonView"});
                else if (state.kinshipDialog) void dispatch({type: "closeKinshipDialog"});
                else if (state.gedcomPreview) void dispatch({type: "cancelGedcomImport"});
                else if (state.statistics) void dispatch({type: "closeStatistics"});
                else if (state.mediaViewer) void dispatch({type: "closeMedia"});
                else if (state.dragRelationMenu) void dispatch({type: "cancelDragRelationMenu"});
                else void dispatch({type: "clearHighlight"});
            } else if (event.altKey && event.key === "ArrowLeft" && state.canGoBack) {
                void dispatch({type: "navigateBack"});
            } else if (event.altKey && event.key === "ArrowRight" && state.canGoForward) {
                void dispatch({type: "navigateForward"});
            }
        };
        window.addEventListener("keydown", onKeyDown);
        return () => window.removeEventListener("keydown", onKeyDown);
    }, [state, dispatch]);

    const submitProjectDialog = async (name: string) => {
        const response =
            projectDialog === "gedcom"
                ? await dispatch({type: "importGedcomDialog", name})
                : await dispatch({type: "createProject", name});
        if (response && (response.isProjectOpen || response.gedcomPreview)) setProjectDialog(null);
    };

    const exportPng = async () => {
        const bridge = getBridge();
        if (!bridge || !state.canvas.layout) {
            void dispatch({type: "reportPngExport", succeeded: false});
            return;
        }
        const path = await bridge.choosePngPath();
        if (!path) return;
        let succeeded = false;
        try {
            succeeded = await exportTreePng(state, bridge, path);
        } catch (failure) {
            console.error("PNG export failed", failure);
        }
        void dispatch({type: "reportPngExport", succeeded});
    };

    // One entry point for every action regardless of where it came from (a button, the native
    // menu, a keyboard shortcut): exportPngDialog needs the renderer's own canvas, so it is
    // intercepted here instead of being forwarded to the backend like everything else.
    const act = (action: DispatchableAction): void => {
        if (action.type === "exportPngDialog") void exportPng();
        else void dispatch(action);
    };

    useEffect(() => {
        return getBridge()?.onMenuAction((action) => {
            if (action.type === "promptNewProject") setProjectDialog("create");
            else if (action.type === "promptImportGedcom") setProjectDialog("gedcom");
            else act(action);
        });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const visibleStatus = state.status && state.status !== "Проект не открыт" && state.status !== "Создайте или откройте проект";

    // Backend status is sticky state, not an event, so the toast is a short-lived view of it:
    // it shows when the status text changes and disappears after a second. Errors stay until closed.
    const [toastStatus, setToastStatus] = useState<string | null>(null);
    const lastStatusRef = useRef(state.status);
    useEffect(() => {
        if (state.status === lastStatusRef.current) return;
        lastStatusRef.current = state.status;
        if (!visibleStatus) return;
        setToastStatus(state.status);
        const timer = window.setTimeout(() => setToastStatus(null), STATUS_TOAST_MS);
        return () => window.clearTimeout(timer);
    }, [state.status, visibleStatus]);

    return (
        <div className="app-shell">
            <TopBar
                state={state}
                dispatch={act}
                onNewProject={() => setProjectDialog("create")}
                onImportGedcom={() => setProjectDialog("gedcom")}
            />
            <div className="workspace">
                <NavRail active={state.mainTab} dispatch={act} onStatistics={() => dispatch({type: "openStatistics"})}/>
                <main className="main-area">
                    <section className="toolbar">
                        <div className="toolbar-group">
                            <button className="icon-button" type="button" aria-label="Назад" disabled={!state.canGoBack}
                                    onClick={() => dispatch({type: "navigateBack"})}>
                                <Icon name="back"/>
                            </button>
                            <button className="icon-button" type="button" aria-label="Вперёд"
                                    disabled={!state.canGoForward} onClick={() => dispatch({type: "navigateForward"})}>
                                <Icon name="forward"/>
                            </button>
                        </div>
                        <span className="toolbar-divider"/>
                        {state.mainTab === "TREE" && <SearchBar state={state} dispatch={act}/>}
                        {state.mainTab === "PERSONS" &&
                            <TableSearch value={tableQuery} placeholder="Найти в списке людей"
                                         onChange={setTableQuery}/>}
                        {state.mainTab === "EVENTS" &&
                            <TableSearch value={tableQuery} placeholder="Найти событие" onChange={setTableQuery}/>}
                        {state.mainTab === "TREE" && <LayoutControls state={state} dispatch={act}/>}
                        <span className="toolbar-spacer"/>
                    </section>

                    <section className="tree-workspace">
                        {(error || toastStatus) && (
                            <div className={error ? "toast-error" : "toast-status"} role={error ? "alert" : "status"}>
                                <span>{error || toastStatus}</span>
                                {error && (
                                    <button aria-label="Закрыть сообщение" type="button" onClick={dismissError}>
                                        <Icon name="x" size={14}/>
                                    </button>
                                )}
                            </div>
                        )}
                        {state.mainTab === "TREE" && (
                            <>
                                <TreeCanvas state={state} dispatch={dispatch}/>
                                {!state.canvas.layout && !state.isProjectOpen && (
                                    <div className="empty-state">
                                        <div className="empty-symbol">
                                            <Icon name="tree" size={30}/>
                                        </div>
                                        <h1>Семейное древо</h1>
                                        <p>Создайте новый проект, откройте существующий или импортируйте GEDCOM, чтобы
                                            начать.</p>
                                        <div className="empty-actions">
                                            <button className="primary-button" type="button"
                                                    onClick={() => setProjectDialog("create")}>
                                                Создать проект
                                            </button>
                                            <button className="secondary-button" type="button"
                                                    onClick={() => dispatch({type: "openProjectDialog"})}>
                                                Открыть проект
                                            </button>
                                            <button className="secondary-button" type="button"
                                                    onClick={() => setProjectDialog("gedcom")}>
                                                Импорт .ged
                                            </button>
                                        </div>
                                    </div>
                                )}
                            </>
                        )}
                        {state.mainTab === "PERSONS" && <PeopleTable state={state} dispatch={act} query={tableQuery}/>}
                        {state.mainTab === "EVENTS" && <EventsTable state={state} dispatch={act} query={tableQuery}/>}
                        {state.mainTab === "DOCUMENTS" && <DocumentsScreen state={state} dispatch={act}/>}
                    </section>

                    <footer className="statusbar">
                        <span>{visibleStatus ? state.status : ""}</span>
                        <span>{state.personCount} человек</span>
                    </footer>
                </main>
                <Inspector state={state} dispatch={act}/>
            </div>

            {state.personForm &&
                <PersonFormDialog form={state.personForm} media={state.selectedMedia} busy={busy} dispatch={dispatch}/>}
            {state.personViewOpen && state.selectedPerson && !state.personForm &&
                <PersonViewDialog state={state} person={state.selectedPerson} dispatch={act}/>}
            {state.relationDialog && <RelationDialog dialog={state.relationDialog} busy={busy} dispatch={dispatch}/>}
            {state.eventForm && <EventFormDialog form={state.eventForm} busy={busy} dispatch={dispatch}/>}
            {state.gedcomPreview && <GedcomPreviewDialog preview={state.gedcomPreview} busy={busy} dispatch={act}/>}
            {state.kinshipDialog && <KinshipDialog dialog={state.kinshipDialog} dispatch={act}/>}
            {state.statistics && <StatisticsDialog statistics={state.statistics} dispatch={act}/>}
            {state.mediaViewer && <MediaViewerDialog viewer={state.mediaViewer} dispatch={act}/>}
            {state.dragRelationMenu && <DragRelationMenuPopup menu={state.dragRelationMenu} dispatch={act}/>}
            {state.personForm?.isDiscardConfirmationVisible && <DiscardDialog dispatch={act}/>}
            {projectDialog && (
                <ProjectNameDialog
                    title={projectDialog === "gedcom" ? "Импорт GEDCOM" : "Новый проект"}
                    submitLabel={projectDialog === "gedcom" ? "Выбрать .ged" : "Создать"}
                    busy={busy}
                    onSubmit={(name) => void submitProjectDialog(name)}
                    onCancel={() => setProjectDialog(null)}
                />
            )}
        </div>
    );
}

export default App;
