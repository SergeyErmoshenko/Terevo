import type { Action } from "../../app/actions";
import type { AppStateDto } from "../../app/dto";
import { Icon } from "../icons/Icon";

export function EventsTable({ state, dispatch }: { state: AppStateDto; dispatch: (action: Action) => void }) {
  const rows = state.eventRows;
  return (
    <section className="data-view">
      <header className="data-heading">
        <div>
          <span className="eyebrow">ХРОНОЛОГИЯ СЕМЬИ</span>
          <h1>
            События <span>{rows.length}</span>
          </h1>
        </div>
        <button className="primary-button" disabled={!state.isProjectOpen} type="button" onClick={() => dispatch({ type: "addEvent" })}>
          <Icon name="plus" />
          Добавить событие
        </button>
      </header>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Событие</th>
              <th>Участники</th>
              <th>Дата</th>
              <th>Место</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.map((event, index) => (
              <tr key={event.id ?? index}>
                <td>
                  <strong>{event.type}</strong>
                </td>
                <td>{event.participants}</td>
                <td>{event.date || "—"}</td>
                <td>{event.place || "—"}</td>
                <td className="table-actions">
                  {event.id && (
                    <>
                      <button className="table-action" type="button" onClick={() => dispatch({ type: "editEvent", id: event.id! })}>
                        Изменить
                      </button>
                      <button
                        className="table-action danger"
                        type="button"
                        onClick={() => {
                          if (window.confirm(`Удалить событие «${event.type}»?`)) dispatch({ type: "deleteEvent", id: event.id! });
                        }}
                      >
                        <Icon name="trash" />
                      </button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {rows.length === 0 && <div className="table-empty">В проекте пока нет событий</div>}
      </div>
    </section>
  );
}
