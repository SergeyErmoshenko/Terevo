import type { Action } from "../../app/actions";
import type { AppStateDto } from "../../app/dto";
import { Icon } from "../icons/Icon";

export function PeopleTable({ state, dispatch }: { state: AppStateDto; dispatch: (action: Action) => void }) {
  const rows = state.personRows;
  return (
    <section className="data-view">
      <header className="data-heading">
        <div>
          <span className="eyebrow">СПРАВОЧНИК</span>
          <h1>
            Люди <span>{rows.length}</span>
          </h1>
        </div>
        <button className="primary-button" disabled={!state.isProjectOpen} type="button" onClick={() => dispatch({ type: "addPerson" })}>
          <Icon name="plus" />
          Добавить
        </button>
      </header>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Имя</th>
              <th>Рождение</th>
              <th>Место жительства</th>
              <th>Род занятий</th>
              <th>Статус</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((person) => (
              <tr key={person.id} onClick={() => dispatch({ type: "selectPerson", id: person.id })}>
                <td>
                  <strong>{person.fullName}</strong>
                </td>
                <td>{person.birthDate || "—"}</td>
                <td>{person.residence || "—"}</td>
                <td>{person.occupation || "—"}</td>
                <td>{person.alive ? "Жив" : "Умер"}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {rows.length === 0 && <div className="table-empty">В проекте пока нет людей</div>}
      </div>
    </section>
  );
}
