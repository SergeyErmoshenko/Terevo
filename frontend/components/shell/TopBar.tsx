import { useState } from "react";
import type { DispatchableAction } from "../../app/actions";
import type { AppStateDto, ThemeMode } from "../../app/dto";
import { Icon } from "../icons/Icon";

const themeLabel: Record<ThemeMode, string> = { LIGHT: "Светлая тема", DARK: "Тёмная тема", SYSTEM: "Системная тема" };

export function TopBar({
  state,
  dispatch,
  onNewProject,
  onImportGedcom,
}: {
  state: AppStateDto;
  dispatch: (action: DispatchableAction) => void;
  onNewProject: () => void;
  onImportGedcom: () => void;
}) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [themeMenuOpen, setThemeMenuOpen] = useState(false);

  return (
    <header className="topbar">
      <span className="brand">
        <span className="brand-mark">T</span>
        <span>Terevo</span>
      </span>
      <div className="topbar-actions">
        <button className="icon-button" type="button" aria-label="Отменить" title={state.undoLabel} disabled={!state.canUndo} onClick={() => dispatch({ type: "undo" })}>
          <Icon name="undo" />
        </button>
        <button className="icon-button" type="button" aria-label="Повторить" title={state.redoLabel} disabled={!state.canRedo} onClick={() => dispatch({ type: "redo" })}>
          <Icon name="redo" />
        </button>
        <span className="toolbar-divider" />
        {state.isProjectOpen && <span className="project-name">{state.projectName}</span>}
        <button className={state.isProjectOpen ? "secondary-button" : "primary-button"} type="button" onClick={onNewProject}>
          Новый проект
        </button>
        <button className="secondary-button" type="button" onClick={() => dispatch({ type: "openProjectDialog" })}>
          Открыть проект
        </button>
        <button className="secondary-button import-button" type="button" onClick={onImportGedcom}>
          Импорт .ged
        </button>
        <div className="menu-anchor">
          <button className="menu-button" type="button" aria-label="Меню" onClick={() => setMenuOpen((open) => !open)}>
            <Icon name="dots" />
          </button>
          {menuOpen && (
            <>
              <div className="context-menu-backdrop" onMouseDown={() => setMenuOpen(false)} />
              <div className="app-menu">
                <button
                  type="button"
                  disabled={!state.isProjectOpen}
                  onClick={() => {
                    setMenuOpen(false);
                    dispatch({ type: "exportGedcomDialog" });
                  }}
                >
                  Экспорт GEDCOM
                </button>
                <button
                  type="button"
                  disabled={!state.isProjectOpen}
                  onClick={() => {
                    setMenuOpen(false);
                    dispatch({ type: "exportPngDialog" });
                  }}
                >
                  Экспорт в PNG
                </button>
                <button
                  type="button"
                  disabled={!state.isProjectOpen}
                  onClick={() => {
                    setMenuOpen(false);
                    dispatch({ type: "exportPdfDialog" });
                  }}
                >
                  Экспорт в PDF
                </button>
                <button
                  type="button"
                  disabled={!state.isProjectOpen}
                  onClick={() => {
                    setMenuOpen(false);
                    dispatch({ type: "openStatistics" });
                  }}
                >
                  Статистика
                </button>
                <hr />
                <button type="button" onClick={() => setThemeMenuOpen((open) => !open)}>
                  {themeLabel[state.themeMode]}
                </button>
                {themeMenuOpen && (
                  <div className="submenu">
                    {(["LIGHT", "DARK", "SYSTEM"] as const).map((mode) => (
                      <button
                        key={mode}
                        type="button"
                        className={mode === state.themeMode ? "selected" : undefined}
                        onClick={() => {
                          dispatch({ type: "changeThemeMode", mode });
                          setMenuOpen(false);
                          setThemeMenuOpen(false);
                        }}
                      >
                        {themeLabel[mode]}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
