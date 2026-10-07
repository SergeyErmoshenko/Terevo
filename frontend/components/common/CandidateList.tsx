import type { PersonSummaryDto } from "../../app/dto";

export function CandidateList({
  people,
  selected,
  onSelect,
  emptyLabel,
}: {
  people: PersonSummaryDto[];
  selected: string | null | undefined;
  onSelect: (id: string) => void;
  emptyLabel: string;
}) {
  if (people.length === 0) return <div className="table-empty">{emptyLabel}</div>;
  return (
    <div className="candidate-list">
      {people.map((person) => (
        <button
          key={person.id}
          type="button"
          className={`candidate-row${selected === person.id ? " selected" : ""}`}
          onClick={() => onSelect(person.id)}
        >
          <span>
            <strong>{person.name}</strong>
            <small>{person.lifeYears}</small>
          </span>
          <span className="radio-mark" aria-hidden>
            {selected === person.id ? "●" : "○"}
          </span>
        </button>
      ))}
    </div>
  );
}
