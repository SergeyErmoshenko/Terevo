import type {ReactNode} from "react";
import type {Action} from "../../app/actions";
import type {AppStateDto, PersonDetailsDto} from "../../app/dto";
import {fileUrl} from "../../app/client";
import {Icon} from "../icons/Icon";
import {Dialog} from "./Dialog";

export function PersonViewDialog({state, person, dispatch}: {
    state: AppStateDto;
    person: PersonDetailsDto;
    dispatch: (action: Action) => void;
}) {
    const close = () => dispatch({type: "closePersonView"});
    return (
        <Dialog
            eyebrow="ЧЕЛОВЕК"
            title={person.name}
            subtitle={person.lifeSpan || "Даты жизни не указаны"}
            width="lg"
            onClose={close}
            footer={
                <>
                    <button className="secondary-button" type="button"
                            onClick={() => dispatch({type: "showOnTree", id: person.id})}>
                        <Icon name="tree"/>
                        Показать на дереве
                    </button>
                    <button className="secondary-button" type="button" onClick={() => dispatch({type: "editPerson"})}>
                        <Icon name="edit"/>
                        Изменить
                    </button>
                    <button className="primary-button" type="button" onClick={close}>
                        Закрыть
                    </button>
                </>
            }
        >
            <div className="person-head">
                <div className={`person-avatar gender-${person.gender.toLowerCase()}`}>
                    {person.photoPath ? <img src={fileUrl(person.photoPath)} alt={person.name}/> : (person.name.trim()[0] ?? "?")}
                </div>
                <div className="person-head-text">
                    <p className="person-years">{person.isAlive ? "Жив" : "Умер"}</p>
                </div>
            </div>
            {person.maidenName && <Row label="Девичья фамилия" value={person.maidenName}/>}
            <Row label="Место рождения" value={person.birthPlace}/>
            <Row label="Место смерти" value={person.deathPlace}/>
            <Row label="Место жительства" value={person.residence}/>
            <Row label="Основное занятие" value={person.occupation}/>
            <Row label="Комментарий" value={person.notes}/>
            {person.customFields.map((field) => (
                <Row key={field.key} label={field.key} value={field.value}/>
            ))}

            <Group title="Родители">
                {state.selectedParents.map((parent) => (
                    <button key={parent.id} type="button" className="relation-chip"
                            onClick={() => dispatch({type: "selectPerson", id: parent.id})}>
                        {parent.name}
                    </button>
                ))}
            </Group>
            <Group title="Супруги">
                {state.selectedSpouses.map((spouse) => (
                    <button key={spouse.person.id} type="button" className="relation-chip spouse-chip"
                            onClick={() => dispatch({type: "selectPerson", id: spouse.person.id})}>
                        <strong>{spouse.person.name}</strong>
                        <small>{spouse.details}</small>
                    </button>
                ))}
            </Group>
            <Group title="Дети">
                {state.selectedChildren.map((child) => (
                    <button key={child.id} type="button" className="relation-chip"
                            onClick={() => dispatch({type: "selectPerson", id: child.id})}>
                        {child.name}
                    </button>
                ))}
            </Group>
            <Group title="Фото и документы">
                {state.selectedMedia.map((media) => (
                    <button key={media.id} type="button" className="media-chip"
                            onClick={() => dispatch({type: "openMedia", id: media.id})}>
                        {media.thumbnailPath ? <img src={fileUrl(media.thumbnailPath)} alt=""/> : <Icon name="fileText"/>}
                        <span>{media.fileName}</span>
                    </button>
                ))}
            </Group>
        </Dialog>
    );
}

function Row({label, value}: { label: string; value: string | null }) {
    return (
        <div className="detail-row">
            <span>{label}</span>
            <p>{value || "—"}</p>
        </div>
    );
}

function Group({title, children}: { title: string; children: ReactNode[] }) {
    if (children.length === 0) return null;
    return (
        <div className="relation-group">
            <span className="relation-group-title">{title}</span>
            {children}
        </div>
    );
}
