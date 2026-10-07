import { useEffect, useState } from "react";
import type { Action, DispatchableAction } from "../../app/actions";
import type { MarriageStatus, ParentKind, RelationDialogDto, RelationFieldsDto, RelationMode } from "../../app/dto";
import { CandidateList } from "../common/CandidateList";
import { EventDateFields } from "../common/EventDateFields";
import { FormField, SegmentedControl, TextField } from "../common/FormField";
import { Dialog } from "./Dialog";

const titleByMode: Record<RelationMode, string> = {
  PARENT: "Добавить родителя",
  CHILD: "Добавить ребёнка",
  SPOUSE: "Добавить супруга",
};

const parentKinds: readonly ParentKind[] = ["BIOLOGICAL", "ADOPTIVE", "STEP", "FOSTER"];
const parentKindLabel: Record<ParentKind, string> = {
  BIOLOGICAL: "Биологический",
  ADOPTIVE: "Усыновитель",
  STEP: "Отчим/мачеха",
  FOSTER: "Опекун",
};
const marriageStatuses: readonly MarriageStatus[] = ["MARRIED", "DIVORCED", "WIDOWED", "PARTNERS"];
const marriageStatusLabel: Record<MarriageStatus, string> = {
  MARRIED: "В браке",
  DIVORCED: "Разведены",
  WIDOWED: "Вдовство",
  PARTNERS: "Партнёры",
};

export function RelationDialog({
  dialog,
  busy,
  dispatch,
}: {
  dialog: RelationDialogDto;
  busy: boolean;
  dispatch: (action: DispatchableAction) => Promise<unknown>;
}) {
  const [fields, setFields] = useState(dialog.fields);
  useEffect(() => setFields(dialog.fields), [dialog]);

  const change = (patch: Partial<RelationFieldsDto>) => {
    const next = { ...fields, ...patch };
    setFields(next);
    void dispatch({ type: "updateRelationDialog", fields: next } satisfies Action);
  };

  return (
    <Dialog
      eyebrow="СВЯЗЬ В ДРЕВЕ"
      title={titleByMode[dialog.mode]}
      subtitle={`Для: ${dialog.source.name}`}
      onClose={() => dispatch({ type: "cancelRelation" })}
      footer={
        <>
          <button className="secondary-button" type="button" onClick={() => dispatch({ type: "cancelRelation" })}>
            Отмена
          </button>
          <button className="primary-button" type="button" disabled={!fields.selected || busy} onClick={() => dispatch({ type: "saveRelation" })}>
            Добавить связь
          </button>
        </>
      }
    >
      <TextField label="Поиск человека" value={fields.query} autoFocus onChange={(query) => change({ query, selected: fields.selected })} />
      <CandidateList people={dialog.people} selected={fields.selected} onSelect={(selected) => change({ selected })} emptyLabel="Подходящие люди не найдены" />
      <button className="secondary-button full-width" type="button" onClick={() => dispatch({ type: "createRelative" })}>
        Создать нового человека
      </button>

      {dialog.mode === "PARENT" && (
        <FormField label="Тип родства">
          <SegmentedControl options={parentKinds} selected={fields.parentKind} label={(kind) => parentKindLabel[kind]} onSelect={(parentKind) => change({ parentKind })} />
        </FormField>
      )}

      {dialog.mode === "SPOUSE" && (
        <>
          <FormField label="Статус брака">
            <SegmentedControl options={marriageStatuses} selected={fields.marriageStatus} label={(status) => marriageStatusLabel[status]} onSelect={(marriageStatus) => change({ marriageStatus })} />
          </FormField>
          <EventDateFields label="Дата свадьбы" input={fields.marriageSince} onChange={(marriageSince) => change({ marriageSince })} />
          <TextField label="Место свадьбы" value={fields.marriagePlace} onChange={(marriagePlace) => change({ marriagePlace })} />
        </>
      )}

      {dialog.mode === "CHILD" && dialog.secondParentCandidates.length > 0 && (
        <FormField label="Второй родитель" wide>
          <select value={fields.secondParent ?? ""} onChange={(event) => change({ secondParent: event.target.value || null })}>
            <option value="">Не указывать</option>
            {dialog.secondParentCandidates.map((person) => (
              <option value={person.id} key={person.id}>
                {person.name}
              </option>
            ))}
          </select>
        </FormField>
      )}

      {dialog.error && <div className="form-feedback">{dialog.error}</div>}
    </Dialog>
  );
}
