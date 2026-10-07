import type { EventDateInputDto, EventDateMode } from "../../app/dto";
import { SegmentedControl, TextField } from "./FormField";

const MODES: readonly EventDateMode[] = ["UNKNOWN", "EXACT", "APPROXIMATE", "RANGE"];

const modeLabel: Record<EventDateMode, string> = {
  EXACT: "Точная",
  APPROXIMATE: "Примерная",
  RANGE: "Диапазон",
  UNKNOWN: "Неизвестна",
};

export function EventDateFields({
  label,
  input,
  error,
  onChange,
}: {
  label: string;
  input: EventDateInputDto;
  error?: string | null;
  onChange: (input: EventDateInputDto) => void;
}) {
  return (
    <div className="field field-wide date-field">
      <span className="field-label">{label}</span>
      <SegmentedControl options={MODES} selected={input.mode} label={(mode) => modeLabel[mode]} onSelect={(mode) => onChange({ ...input, mode })} />
      {(input.mode === "EXACT" || input.mode === "APPROXIMATE") && (
        <TextField label={label} value={input.value} placeholder="ДД.ММ.ГГГГ" error={error} onChange={(value) => onChange({ ...input, value })} />
      )}
      {input.mode === "RANGE" && (
        <div className="date-range">
          <TextField label="Начало" value={input.value} placeholder="ДД.ММ.ГГГГ" error={error} onChange={(value) => onChange({ ...input, value })} />
          <TextField label="Конец" value={input.end} placeholder="ДД.ММ.ГГГГ" error={error} onChange={(end) => onChange({ ...input, end })} />
        </div>
      )}
    </div>
  );
}
