import { useEffect, useState } from "react";
import type { Action } from "../../app/actions";
import type { KinshipDialogDto } from "../../app/dto";
import { CandidateList } from "../common/CandidateList";
import { TextField } from "../common/FormField";
import { Dialog } from "./Dialog";

export function KinshipDialog({ dialog, dispatch }: { dialog: KinshipDialogDto; dispatch: (action: Action) => void }) {
  const [query, setQuery] = useState(dialog.query);
  useEffect(() => setQuery(dialog.query), [dialog.source.id]);

  return (
    <Dialog
      eyebrow="РОДСТВО"
      title="Кем приходится"
      subtitle={dialog.source.name}
      onClose={() => dispatch({ type: "closeKinshipDialog" })}
      footer={
        <button className="primary-button" type="button" onClick={() => dispatch({ type: "closeKinshipDialog" })}>
          Закрыть
        </button>
      }
    >
      <TextField
        label="Поиск человека"
        value={query}
        autoFocus
        onChange={(value) => {
          setQuery(value);
          dispatch({ type: "updateKinshipDialog", query: value, target: dialog.target });
        }}
      />
      <CandidateList
        people={dialog.people}
        selected={dialog.target}
        onSelect={(target) => dispatch({ type: "updateKinshipDialog", query, target })}
        emptyLabel="Никого не найдено"
      />
      {dialog.term && <p className="kinship-result">Родство: {dialog.term}</p>}
    </Dialog>
  );
}
