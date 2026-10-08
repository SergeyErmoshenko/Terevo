import type {Action} from "../../app/actions";
import type {AppStateDto} from "../../app/dto";
import {Icon} from "../icons/Icon";
import {matchesQuery} from "../shell/TableSearch";

export function PeopleTable({state, dispatch, query}: {
    state: AppStateDto;
    dispatch: (action: Action) => void;
    query: string
}) {
    const rows = state.personRows.filter((person) => matchesQuery(query, person.searchText, person.residence, person.occupation, person.birthDate));
    return (
        <section className="data-view">
            <header className="data-heading">
                <div>
                    <span className="eyebrow">СПРАВОЧНИК</span>
                    <h1>
                        Люди <span>{rows.length}</span>
                    </h1>
                </div>
                <button className="primary-button" disabled={!state.isProjectOpen} type="button"
                        onClick={() => dispatch({type: "addPerson"})}>
                    <Icon name="plus"/>
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
                        <tr
                            key={person.id}
                            title="Двойной щелчок — показать на дереве"
                            onClick={() => dispatch({type: "selectPerson", id: person.id})}
                            onDoubleClick={() => dispatch({type: "showOnTree", id: person.id})}
                        >
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
                {rows.length === 0 &&
                    <div className="table-empty">{query ? "Ничего не найдено" : "В проекте пока нет людей"}</div>}
            </div>
        </section>
    );
}
