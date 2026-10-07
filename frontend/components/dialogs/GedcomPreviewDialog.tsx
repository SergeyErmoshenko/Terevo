import type { Action } from "../../app/actions";
import type { GedcomPreviewDto } from "../../app/dto";
import { Dialog } from "./Dialog";

export function GedcomPreviewDialog({
  preview,
  busy,
  dispatch,
}: {
  preview: GedcomPreviewDto;
  busy: boolean;
  dispatch: (action: Action) => void;
}) {
  return (
    <Dialog
      eyebrow="ИМПОРТ GEDCOM"
      title="Проверка файла завершена"
      onClose={() => dispatch({ type: "cancelGedcomImport" })}
      footer={
        <>
          <button className="secondary-button" type="button" onClick={() => dispatch({ type: "cancelGedcomImport" })}>
            Отмена
          </button>
          <button className="primary-button" type="button" disabled={busy} onClick={() => dispatch({ type: "confirmGedcomImport" })}>
            {busy ? "Импорт…" : "Импортировать"}
          </button>
        </>
      }
    >
      <div className="stats-grid">
        <Stat label="Людей" value={preview.people} />
        <Stat label="Семей" value={preview.families} />
      </div>
      {preview.skippedTags.length > 0 && (
        <div className="gedcom-skipped">
          <span>Нераспознанные теги</span>
          <p>{preview.skippedTags.join(", ")}</p>
        </div>
      )}
      {preview.error && <div className="form-feedback">{preview.error}</div>}
    </Dialog>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="stat-item">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
