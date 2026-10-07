import type { ReactNode } from "react";

export function FormField({
  label,
  wide,
  children,
}: {
  label: string;
  wide?: boolean;
  children: ReactNode;
}) {
  return (
    <label className={`field${wide ? " field-wide" : ""}`}>
      <span className="field-label">{label}</span>
      {children}
    </label>
  );
}

export function TextField({
  label,
  value,
  onChange,
  placeholder,
  error,
  wide,
  multiline,
  autoFocus,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  error?: string | null;
  wide?: boolean;
  multiline?: boolean;
  autoFocus?: boolean;
}) {
  return (
    <FormField label={label} wide={wide}>
      {multiline ? (
        <textarea rows={3} value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} />
      ) : (
        <input
          autoFocus={autoFocus}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder={placeholder}
          aria-invalid={error ? true : undefined}
        />
      )}
      {error && <span className="field-error">{error}</span>}
    </FormField>
  );
}

export function SegmentedControl<T extends string>({
  options,
  selected,
  label,
  onSelect,
}: {
  options: readonly T[];
  selected: T;
  label: (value: T) => string;
  onSelect: (value: T) => void;
}) {
  return (
    <div className="segmented" role="radiogroup">
      {options.map((option) => (
        <button
          key={option}
          type="button"
          role="radio"
          aria-checked={option === selected}
          className={`segmented-option${option === selected ? " selected" : ""}`}
          onClick={() => onSelect(option)}
        >
          {label(option)}
        </button>
      ))}
    </div>
  );
}
