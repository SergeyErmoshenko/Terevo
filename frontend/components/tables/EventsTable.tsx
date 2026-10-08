import type { Action } from "../../app/actions";
import type { AppStateDto } from "../../app/dto";
import { Icon } from "../icons/Icon";
import { matchesQuery } from "../shell/TableSearch";

function pluralDays(count: number): string {
  const mod100 = count % 100;
  const mod10 = count % 10;
  if (mod100 >= 11 && mod100 <= 14) return "дней";
  if (mod10 === 1) return "день";
  if (mod10 >= 2 && mod10 <= 4) return "дня";
  return "дней";
}

function pluralYears(count: number): string {
  const mod100 = count % 100;
  const mod10 = count % 10;
  if (mod100 >= 11 && mod100 <= 14) return "лет";
  if (mod10 === 1) return "год";
  if (mod10 >= 2 && mod10 <= 4) return "года";
  return "лет";
}

// "Сегодня · 35 лет", "Через 12 дней · 36 лет": days to the next anniversary and the age it marks.
function anniversaryText(days: number | null, yearsPassed: number | null): string {
  if (days === null) return "—";
  const when = days === 0 ? "Сегодня" : days === 1 ? "Завтра" : `Через ${days} ${pluralDays(days)}`;
  if (yearsPassed === null) return when;
  const turning = days === 0 ? yearsPassed : yearsPassed + 1;
  return `${when} · ${turning} ${pluralYears(turning)}`;
}

export function EventsTable({ state, dispatch, query }: { state: AppStateDto; dispatch: (action: Action) => void; query: string }) {
  const rows = state.eventRows.filter((event) => matchesQuery(query, event.type, event.participants, event.place, event.date));
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
              <th>До годовщины</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.map((event, index) => (
              <tr key={event.id ?? index}>
                <td className="nowrap-cell">
                  <strong>{event.type}</strong>
                </td>
                <td>{event.participants}</td>
                <td>{event.date || "—"}</td>
                <td>{event.place || "—"}</td>
                <td className="anniversary-cell">{anniversaryText(event.daysUntilAnniversary, event.yearsPassed)}</td>
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
        {rows.length === 0 && <div className="table-empty">{query ? "Ничего не найдено" : "В проекте пока нет событий"}</div>}
      </div>
    </section>
  );
}
