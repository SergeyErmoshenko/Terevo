import { useState } from "react";
import { TextField } from "../common/FormField";
import { Dialog } from "./Dialog";

export function ProjectNameDialog({
  title,
  submitLabel,
  busy,
  onSubmit,
  onCancel,
}: {
  title: string;
  submitLabel: string;
  busy: boolean;
  onSubmit: (name: string) => void;
  onCancel: () => void;
}) {
  const [name, setName] = useState("");
  return (
    <Dialog
      eyebrow="ПРОЕКТ"
      title={title}
      onClose={onCancel}
      width="sm"
      footer={
        <>
          <button className="secondary-button" type="button" onClick={onCancel}>
            Отмена
          </button>
          <button className="primary-button" type="button" disabled={!name.trim() || busy} onClick={() => onSubmit(name.trim())}>
            {submitLabel}
          </button>
        </>
      }
    >
      <form
        onSubmit={(event) => {
          event.preventDefault();
          if (name.trim()) onSubmit(name.trim());
        }}
      >
        <TextField label="Название проекта" value={name} autoFocus placeholder="Например: Семья Ивановых" onChange={setName} />
      </form>
    </Dialog>
  );
}
