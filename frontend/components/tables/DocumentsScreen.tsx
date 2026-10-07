import { useMemo, useState } from "react";
import type { DispatchableAction } from "../../app/actions";
import type { AppStateDto } from "../../app/dto";
import { Icon } from "../icons/Icon";

export function DocumentsScreen({ state, dispatch }: { state: AppStateDto; dispatch: (action: DispatchableAction) => void }) {
  const [query, setQuery] = useState("");
  const filtered = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase("ru");
    if (!needle) return state.personRows;
    return state.personRows.filter((row) => row.fullName.toLocaleLowerCase("ru").includes(needle));
  }, [state.personRows, query]);

  return (
    <section className="documents-view">
      <aside className="documents-people">
        <input className="search-input" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Поиск" />
        <div className="documents-people-list">
          {filtered.map((row) => (
            <button
              key={row.id}
              type="button"
              className={`documents-person${state.selectedPerson?.id === row.id ? " active" : ""}`}
              onClick={() => dispatch({ type: "selectPerson", id: row.id })}
            >
              {row.fullName}
            </button>
          ))}
        </div>
      </aside>
      <div
        className="documents-panel"
        onDragOver={(event) => event.preventDefault()}
        onDrop={(event) => {
          event.preventDefault();
          const paths = Array.from(event.dataTransfer.files)
            .map((file) => window.terevo?.pathForFile(file))
            .filter((path): path is string => Boolean(path));
          if (paths.length > 0) dispatch({ type: "addMedia", paths });
        }}
      >
        {!state.selectedPerson ? (
          <div className="inspector-empty">Выберите человека слева</div>
        ) : (
          <>
            <h2>{state.selectedPerson.name}</h2>
            <button className="secondary-button" type="button" onClick={() => dispatch({ type: "chooseMediaDialog" })}>
              <Icon name="paperclip" />
              Добавить файл
            </button>
            <p className="field-hint">Можно перетащить файлы прямо сюда</p>
            <div className="documents-media-list">
              {state.selectedMedia.map((media) => (
                <div className="documents-media-row" key={media.id}>
                  <Icon name="fileText" />
                  <span>{media.fileName}</span>
                  <button className="table-action" type="button" onClick={() => dispatch({ type: "openMedia", id: media.id })}>
                    <Icon name="externalLink" />
                    Открыть
                  </button>
                  <button className="table-action danger" type="button" onClick={() => dispatch({ type: "removeMedia", id: media.id })}>
                    <Icon name="trash" />
                  </button>
                </div>
              ))}
              {state.selectedMedia.length === 0 && <div className="table-empty">Файлов пока нет</div>}
            </div>
          </>
        )}
      </div>
    </section>
  );
}
