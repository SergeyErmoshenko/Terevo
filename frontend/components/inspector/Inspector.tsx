import type { Action, DispatchableAction } from "../../app/actions";
import type { AppStateDto } from "../../app/dto";
import { fileUrl } from "../../app/client";
import { Icon } from "../icons/Icon";

export function Inspector({ state, dispatch }: { state: AppStateDto; dispatch: (action: DispatchableAction) => void }) {
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
            <div className="person-head">
              <div className="person-avatar-wrap">
                <div className={`person-avatar gender-${person.gender.toLowerCase()}`}>
                  {person.photoPath ? <img src={fileUrl(person.photoPath)} alt={person.name} /> : (person.name.trim()[0] ?? "?")}
                </div>
                <button
                  className="avatar-edit"
                  type="button"
                  aria-label="Добавить фото"
                  title="Добавить фото или документ"
                  onClick={() => dispatch({ type: "chooseMediaDialog", kind: "photo" })}
                >
                  <Icon name="photo" size={14} />
                </button>
              </div>
              <div className="person-head-text">
                <h3>{person.name}</h3>
                <p className="person-years">{person.lifeSpan || "Даты жизни не указаны"}</p>
              </div>
            </div>
            <div className="person-actions">
              <button className="secondary-button" type="button" onClick={() => dispatch({ type: "editPerson" })}>
                <Icon name="edit" />
                Изменить
              </button>
              <button className="secondary-button" type="button" onClick={() => dispatch({ type: "chooseMediaDialog", kind: "photo" })}>
                <Icon name="photo" />
                Добавить фото
              </button>
              <button className="secondary-button" type="button" onClick={() => dispatch({ type: "chooseMediaDialog", kind: "document" })}>
                <Icon name="fileText" />
                PDF / DOCX
              </button>
              <button className="secondary-button person-actions-wide" type="button" onClick={() => dispatch({ type: "showOnTree", id: person.id })}>
                <Icon name="tree" />
                Показать на дереве
              </button>
              <button className="secondary-button person-actions-wide" type="button" onClick={() => dispatch({ type: "openKinshipDialog" })}>
                <Icon name="link" />
                Кем приходится
              </button>
            </div>
            {person.maidenName && <Detail label="Девичья фамилия" value={person.maidenName} />}
            {person.birthPlace && <Detail label="Место рождения" value={person.birthPlace} />}
            {person.deathPlace && <Detail label="Место смерти" value={person.deathPlace} />}
            {person.residence && <Detail label="Место жительства" value={person.residence} />}
            {person.occupation && <Detail label="Основное занятие" value={person.occupation} />}
            {person.notes && <Detail label="Комментарий" value={person.notes} />}
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

            <div className="person-danger">
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
                Удалить человека
              </button>
            </div>
          </div>
          <div className="inspector-bottom">
            <span className="relation-group-title">Добавить</span>
            <div className="relation-actions">
              <button className="secondary-button" type="button" onClick={() => dispatch({ type: "addRelative", mode: "PARENT" })}>
                <Icon name="plus" size={14} />
                Родителя
              </button>
              <button className="secondary-button" type="button" onClick={() => dispatch({ type: "addRelative", mode: "CHILD" })}>
                <Icon name="plus" size={14} />
                Ребёнка
              </button>
              <button className="secondary-button" type="button" onClick={() => dispatch({ type: "addRelative", mode: "SPOUSE" })}>
                <Icon name="plus" size={14} />
                {person.gender === "MALE" ? "Супругу" : "Супруга"}
              </button>
              <button className="secondary-button" type="button" onClick={() => dispatch({ type: "addPerson" })}>
                <Icon name="userPlus" size={14} />
                Человека
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
