import type { Action } from "../../app/actions";
import type { AppStateDto, Gender } from "../../app/dto";
import { fileUrl } from "../../app/client";
import { Icon } from "../icons/Icon";

const spouseLabel: Record<Gender, string> = {
  MALE: "Добавить супругу",
  FEMALE: "Добавить супруга",
  UNKNOWN: "Добавить супруга",
};

export function Inspector({ state, dispatch }: { state: AppStateDto; dispatch: (action: Action) => void }) {
  const person = state.selectedPerson;
  const collapsed = state.sidebarCollapsed;

  return (
    <aside className={`inspector${collapsed ? " collapsed" : ""}`}>
      <div className="inspector-heading">
        {!collapsed && <h2>Сведения</h2>}
        <button className="icon-button" type="button" aria-label={collapsed ? "Развернуть панель" : "Свернуть панель"} onClick={() => dispatch({ type: "toggleSidebar" })}>
          <Icon name="panelRight" />
        </button>
      </div>
      {collapsed ? null : person ? (
        <>
          <div className="person-details">
            <div className={`person-avatar gender-${person.gender.toLowerCase()}`}>
              {person.photoPath ? <img src={fileUrl(person.photoPath)} alt={person.name} /> : (person.name.trim()[0] ?? "?")}
            </div>
            <h3>{person.name}</h3>
            <p className="person-years">{person.lifeSpan || "Даты жизни не указаны"}</p>
            {person.maidenName && <Detail label="Девичья фамилия" value={person.maidenName} />}
            {person.birthPlace && <Detail label="Место рождения" value={person.birthPlace} />}
            {person.deathPlace && <Detail label="Место смерти" value={person.deathPlace} />}
            {person.residence && <Detail label="Место жительства" value={person.residence} />}
            {person.occupation && <Detail label="Основное занятие" value={person.occupation} />}
            {person.notes && <Detail label="Заметки" value={person.notes} />}
            {person.customFields.map((field) => (
              <Detail key={field.key} label={field.key} value={field.value} />
            ))}

            <RelationList title="Родители" people={state.selectedParents} dispatch={dispatch} />
            <RelationList title="Дети" people={state.selectedChildren} dispatch={dispatch} />
            {state.selectedSpouses.length > 0 && (
              <div className="relation-group">
                <span className="relation-group-title">Супруги</span>
                {state.selectedSpouses.map((spouse) => (
                  <button key={spouse.person.id} type="button" className="relation-chip spouse-chip" onClick={() => dispatch({ type: "selectPerson", id: spouse.person.id })}>
                    <strong>{spouse.person.name}</strong>
                    <small>{spouse.details}</small>
                  </button>
                ))}
              </div>
            )}

            {state.selectedMedia.length > 0 && (
              <div className="relation-group">
                <span className="relation-group-title">Фото и документы</span>
                {state.selectedMedia.map((media) => (
                  <button key={media.id} type="button" className="media-chip" onClick={() => dispatch({ type: "openMedia", id: media.id })}>
                    {media.thumbnailPath ? <img src={fileUrl(media.thumbnailPath)} alt="" /> : <Icon name="fileText" />}
                    <span>{media.fileName}</span>
                  </button>
                ))}
              </div>
            )}

            <div className="person-actions">
              <button className="secondary-button" type="button" onClick={() => dispatch({ type: "editPerson" })}>
                <Icon name="edit" />
                Изменить
              </button>
              <button className="secondary-button" type="button" onClick={() => dispatch({ type: "openKinshipDialog" })}>
                <Icon name="link" />
                Кем приходится
              </button>
              <button
                className="secondary-button danger-button"
                type="button"
                onClick={() => {
                  if (window.confirm(`${person.name} будет удалён из дерева вместе со всеми его связями. Действие можно отменить через «Правка → Отменить».`)) {
                    dispatch({ type: "deletePerson" });
                  }
                }}
              >
                <Icon name="trash" />
                Удалить
              </button>
            </div>
          </div>
          <div className="inspector-bottom">
            <div className="relation-actions">
              <button className="secondary-button" type="button" onClick={() => dispatch({ type: "addRelative", mode: "PARENT" })}>
                Добавить родителя
              </button>
              <button className="secondary-button" type="button" onClick={() => dispatch({ type: "addRelative", mode: "CHILD" })}>
                Добавить ребёнка
              </button>
              <button className="secondary-button" type="button" onClick={() => dispatch({ type: "addRelative", mode: "SPOUSE" })}>
                {spouseLabel[person.gender]}
              </button>
              <button className="secondary-button" type="button" onClick={() => dispatch({ type: "addPerson" })}>
                <Icon name="userPlus" />
                Новый человек
              </button>
            </div>
          </div>
        </>
      ) : (
        <>
          <div className="inspector-empty">{state.isProjectOpen ? "Выберите человека на древе" : ""}</div>
          <div className="inspector-bottom">
            <button className="primary-button full-width" type="button" disabled={!state.isProjectOpen} onClick={() => dispatch({ type: "addPerson" })}>
              <Icon name="plus" />
              Добавить человека
            </button>
          </div>
        </>
      )}
    </aside>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div className="detail-row">
      <span>{label}</span>
      <p>{value}</p>
    </div>
  );
}

function RelationList({
  title,
  people,
  dispatch,
}: {
  title: string;
  people: { id: string; name: string }[];
  dispatch: (action: Action) => void;
}) {
  if (people.length === 0) return null;
  return (
    <div className="relation-group">
      <span className="relation-group-title">{title}</span>
      {people.map((person) => (
        <button key={person.id} type="button" className="relation-chip" onClick={() => dispatch({ type: "selectPerson", id: person.id })}>
          {person.name}
        </button>
      ))}
    </div>
  );
}
