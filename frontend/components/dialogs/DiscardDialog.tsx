import type { Action } from "../../app/actions";
import { Dialog } from "./Dialog";

export function DiscardDialog({ dispatch }: { dispatch: (action: Action) => void }) {
  return (
    <Dialog
      eyebrow="НЕСОХРАНЁННЫЕ ИЗМЕНЕНИЯ"
      title="Отменить изменения?"
      width="sm"
      footer={
        <>
          <button className="secondary-button" type="button" onClick={() => dispatch({ type: "keepEditingPerson" })}>
            Продолжить редактирование
          </button>
          <button className="primary-button danger-button" type="button" onClick={() => dispatch({ type: "confirmDiscardPerson" })}>
            Не сохранять
          </button>
        </>
      }
    >
      <p className="dialog-plain-text">Введённые данные не будут сохранены.</p>
    </Dialog>
  );
}
