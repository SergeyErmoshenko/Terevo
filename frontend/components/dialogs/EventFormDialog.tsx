import { useEffect, useState } from "react";
import type { Action, DispatchableAction } from "../../app/actions";
import type { EventFormDto, EventFormFieldsDto, ParticipantDto } from "../../app/dto";
import { Icon } from "../icons/Icon";
import { EventDateFields } from "../common/EventDateFields";
import { TextField } from "../common/FormField";
import { Dialog } from "./Dialog";

function updated<T>(list: T[], index: number, value: T): T[] {
  return list.map((item, i) => (i === index ? value : item));
}

export function EventFormDialog({
  form,
  busy,
  dispatch,
}: {
  form: EventFormDto;
  busy: boolean;
  dispatch: (action: DispatchableAction) => Promise<unknown>;
}) {
  const [fields, setFields] = useState(form.fields);
  useEffect(() => setFields(form.fields), [form]);

  const change = (patch: Partial<EventFormFieldsDto>) => {
    const next = { ...fields, ...patch };
    setFields(next);
    void dispatch({ type: "updateEventForm", fields: next } satisfies Action);
  };

  const updateParticipant = (index: number, patch: Partial<ParticipantDto>) => {
    change({ participants: updated(fields.participants, index, { ...fields.participants[index], ...patch }) });
  };
  const removeParticipant = (index: number) => change({ participants: fields.participants.filter((_, i) => i !== index) });
  const addParticipant = () => change({ participants: [...fields.participants, { personId: null, query: "", role: "" }] });

  return (
    <Dialog
      eyebrow="ХРОНОЛОГИЯ СЕМЬИ"
      title={form.isNew ? "Новое событие" : "Изменить событие"}
      onClose={() => dispatch({ type: "cancelEvent" })}
      footer={
        <>
          <button className="secondary-button" type="button" onClick={() => dispatch({ type: "cancelEvent" })}>
            Отмена
          </button>
          <button className="primary-button" type="button" disabled={!form.canSave || busy} onClick={() => dispatch({ type: "saveEvent" })}>
            Сохранить
          </button>
        </>
      }
    >
      <div className="form-grid">
        <TextField label="Название события" value={fields.type} wide autoFocus onChange={(type) => change({ type })} />
        <EventDateFields label="Дата" input={fields.date} onChange={(date) => change({ date })} />
        <TextField label="Место" value={fields.place} onChange={(place) => change({ place })} />
        <TextField label="Заметки" value={fields.notes} wide multiline onChange={(notes) => change({ notes })} />

        <div className="field field-wide">
          <span className="field-label">Участники</span>
          {fields.participants.map((participant, index) => {
            const selectedPerson = participant.personId ? form.people.find((person) => person.id === participant.personId) : undefined;
            const query = participant.query.trim().toLocaleLowerCase("ru");
            const matches = !selectedPerson
              ? form.people.filter((person) => query === "" || person.name.toLocaleLowerCase("ru").includes(query))
              : [];
            return (
              <div className="participant-row" key={index}>
                <div className="participant-picker">
                  <input
                    value={selectedPerson?.name ?? participant.query}
                    placeholder="Поиск человека"
                    onChange={(event) => updateParticipant(index, { personId: null, query: event.target.value })}
                  />
                  {matches.length > 0 && (
                    <div className="participant-matches">
                      {matches.slice(0, 8).map((person) => (
                        <button key={person.id} type="button" onClick={() => updateParticipant(index, { personId: person.id, query: "" })}>
                          {person.name}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
                <input
                  className="participant-role"
                  value={participant.role}
                  placeholder="Роль"
                  onChange={(event) => updateParticipant(index, { role: event.target.value })}
                />
                <button type="button" className="icon-button" aria-label="Удалить участника" onClick={() => removeParticipant(index)}>
                  <Icon name="trash" />
                </button>
              </div>
            );
          })}
          <button className="secondary-button" type="button" onClick={addParticipant}>
            Добавить участника
          </button>
        </div>

        {form.blockingError && <div className="form-feedback">{form.blockingError}</div>}
      </div>
    </Dialog>
  );
}
