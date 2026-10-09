import { useEffect, useState } from "react";
import type { Action, DispatchableAction } from "../../app/actions";
import { fileUrl } from "../../app/client";
import type { CustomFieldDto, Gender, MediaDto, PersonFormDto, PersonFormFieldsDto } from "../../app/dto";
import { Icon } from "../icons/Icon";
import { EventDateFields } from "../common/EventDateFields";
import { FormField, SegmentedControl, TextField } from "../common/FormField";
import { Dialog } from "./Dialog";

const GENDERS: readonly Gender[] = ["UNKNOWN", "MALE", "FEMALE"];
const genderLabel: Record<Gender, string> = { MALE: "Мужской", FEMALE: "Женский", UNKNOWN: "Не указан" };
const SUGGESTION_LIMIT = 5;

function fileName(path: string): string {
  return path.split(/[/\\]/).pop() ?? path;
}

export function PersonFormDialog({
  form,
  media,
  busy,
  dispatch,
}: {
  form: PersonFormDto;
  media: MediaDto[];
  busy: boolean;
  dispatch: (action: DispatchableAction) => Promise<unknown>;
}) {
  const [fields, setFields] = useState(form.fields);
  useEffect(() => setFields(form.fields), [form]);

  const change = (patch: Partial<PersonFormFieldsDto>) => {
    const next = { ...fields, ...patch };
    setFields(next);
    void dispatch({ type: "updatePersonForm", fields: next } satisfies Action);
  };

  const submit = async () => {
    if (!form.canSave) return;
    await dispatch({ type: "savePerson" });
  };

  const genderOptions = form.requiredGender ? [form.requiredGender] : GENDERS;

  const updateCustomField = (index: number, patch: Partial<CustomFieldDto>) => {
    change({ customFields: fields.customFields.map((field, i) => (i === index ? { ...field, ...patch } : field)) });
  };
  const removeCustomField = (index: number) => change({ customFields: fields.customFields.filter((_, i) => i !== index) });
  const addCustomField = (key = "") => change({ customFields: [...fields.customFields, { key, value: "" }] });

  const addMediaPaths = async (paths: string[]) => {
    if (paths.length === 0) return;
    await dispatch({ type: "addPersonFormMedia", paths });
  };

  // The file picker goes first: if it is cancelled (null) the old file stays; otherwise the
  // old one is removed and the new one is attached when the form is saved.
  const replaceMedia = async (id: string) => {
    const picked = await dispatch({ type: "choosePersonFormMediaDialog" });
    if (picked) await dispatch({ type: "removeMedia", id });
  };

  const existingMedia = form.isNew ? [] : media;

  return (
    <Dialog
      eyebrow={form.isNew ? "НОВАЯ ЗАПИСЬ" : "ПРОФИЛЬ ЧЕЛОВЕКА"}
      title={form.isNew ? "Добавить человека" : "Изменить данные"}
      onClose={() => dispatch({ type: "cancelPerson" })}
      width="lg"
      footer={
        <>
          <button className="secondary-button" type="button" onClick={() => dispatch({ type: "cancelPerson" })}>
            Отмена
          </button>
          <button className="primary-button" type="button" disabled={!form.canSave || busy} onClick={submit}>
            {busy ? "Сохранение…" : "Сохранить"}
          </button>
        </>
      }
    >
      <div
        className="form-grid"
        onDragOver={(event) => event.preventDefault()}
        onDrop={(event) => {
          event.preventDefault();
          const paths = Array.from(event.dataTransfer.files)
            .map((file) => window.terevo?.pathForFile(file))
            .filter((path): path is string => Boolean(path));
          void addMediaPaths(paths);
        }}
      >
        <div className="field-wide photo-block">
          <div className="photo-block-preview" aria-hidden="true">
            <Icon name="photo" size={26} />
          </div>
          <div className="photo-block-text">
            <strong>Фото и документы</strong>
            <span>
              {form.pendingMediaPaths.length > 0
                ? `Выбрано файлов: ${form.pendingMediaPaths.length}`
                : "Фотографии, PDF и документы Word. Файлы можно перетащить в это окно."}
            </span>
          </div>
          <div className="photo-block-actions">
            <button className="secondary-button" type="button" onClick={() => dispatch({ type: "choosePersonFormMediaDialog", kind: "photo" })}>
              <Icon name="photo" />
              Добавить фото
            </button>
            <button className="secondary-button" type="button" onClick={() => dispatch({ type: "choosePersonFormMediaDialog", kind: "document" })}>
              <Icon name="fileText" />
              Добавить PDF / DOCX
            </button>
          </div>
        </div>
        {existingMedia.length > 0 && (
          <ul className="existing-media-list field-wide">
            {existingMedia.map((media) => (
              <li key={media.id}>
                <span className="existing-media-thumb">
                  {media.thumbnailPath ? <img src={fileUrl(media.thumbnailPath)} alt="" /> : <Icon name="fileText" />}
                </span>
                <span className="existing-media-name">{media.fileName}</span>
                <button className="secondary-button" type="button" disabled={busy} onClick={() => void replaceMedia(media.id)}>
                  Заменить
                </button>
                <button
                  className="icon-button danger-icon"
                  type="button"
                  aria-label={`Удалить файл ${media.fileName}`}
                  title="Удалить"
                  disabled={busy}
                  onClick={() => void dispatch({ type: "removeMedia", id: media.id })}
                >
                  <Icon name="trash" />
                </button>
              </li>
            ))}
            <li className="existing-media-hint">Удаление и замена применяются сразу и не зависят от кнопки «Сохранить».</li>
          </ul>
        )}
        {form.pendingMediaPaths.length > 0 && (
          <ul className="pending-media-list field-wide">
            {form.pendingMediaPaths.map((path) => (
              <li key={path}>
                <span>{fileName(path)}</span>
                <button type="button" className="icon-button" aria-label="Удалить файл" onClick={() => dispatch({ type: "removePendingPersonMedia", path })}>
                  <Icon name="trash" />
                </button>
              </li>
            ))}
          </ul>
        )}

        <h3 className="form-section-title">Личные данные</h3>
        <TextField label="Фамилия" value={fields.surname} autoFocus onChange={(surname) => change({ surname })} />
        <TextField label="Имя" value={fields.givenName} onChange={(givenName) => change({ givenName })} />
        <TextField label="Отчество" value={fields.patronymic} onChange={(patronymic) => change({ patronymic })} />
        {fields.gender === "FEMALE" && (
          <TextField label="Девичья фамилия" value={fields.maidenName} onChange={(maidenName) => change({ maidenName })} />
        )}
        <FormField label="Пол" wide>
          <SegmentedControl
            options={genderOptions}
            selected={fields.gender}
            label={(gender) => genderLabel[gender]}
            onSelect={(gender) => change({ gender, maidenName: gender === "FEMALE" ? fields.maidenName : "" })}
          />
        </FormField>

        <h3 className="form-section-title">Рождение</h3>
        <EventDateFields label="Дата рождения" input={fields.birth} onChange={(birth) => change({ birth })} />
        <TextField label="Место рождения" value={fields.birthPlace} onChange={(birthPlace) => change({ birthPlace })} />
        <TextField label="Место жительства" value={fields.residence} onChange={(residence) => change({ residence })} />

        <h3 className="form-section-title">Жизнь</h3>
        <label className="field field-check field-wide">
          <input
            type="checkbox"
            checked={fields.isAlive}
            onChange={(event) => {
              const isAlive = event.target.checked;
              change(isAlive ? { isAlive, death: { mode: "UNKNOWN", value: "", end: "", precision: "YEAR" }, deathPlace: "" } : { isAlive, death: { ...fields.death, mode: "EXACT" } });
            }}
          />
          Жив(а)
        </label>
        {!fields.isAlive && (
          <>
            <EventDateFields label="Дата смерти" input={fields.death} onChange={(death) => change({ death })} />
            <TextField label="Место смерти" value={fields.deathPlace} wide onChange={(deathPlace) => change({ deathPlace })} />
          </>
        )}
        <TextField label="Основное занятие" value={fields.occupation} wide onChange={(occupation) => change({ occupation })} />
        <TextField label="Комментарий" value={fields.notes} wide multiline onChange={(notes) => change({ notes })} />

        <h3 className="form-section-title">Дополнительные поля</h3>
        <div className="field field-wide">
          {form.customFieldSuggestions
            .filter((suggestion) => !fields.customFields.some((field) => field.key.toLowerCase() === suggestion.toLowerCase()))
            .slice(0, SUGGESTION_LIMIT)
            .map((suggestion) => (
              <button key={suggestion} type="button" className="chip-button" onClick={() => addCustomField(suggestion)}>
                + {suggestion}
              </button>
            ))}
          {fields.customFields.map((field, index) => (
            <div className="custom-field-row" key={index}>
              <TextField label="Название поля" value={field.key} onChange={(key) => updateCustomField(index, { key })} />
              <TextField label="Значение" value={field.value} onChange={(value) => updateCustomField(index, { value })} />
              <button type="button" className="icon-button" aria-label="Удалить поле" onClick={() => removeCustomField(index)}>
                <Icon name="trash" />
              </button>
            </div>
          ))}
          <button className="secondary-button" type="button" onClick={() => addCustomField()}>
            Добавить поле
          </button>
        </div>

        {form.blockingError && <div className="form-feedback">{form.blockingError}</div>}
        {form.warnings.map((warning) => (
          <div className="form-feedback" key={warning}>
            {warning}
          </div>
        ))}
      </div>
    </Dialog>
  );
}
