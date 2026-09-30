import type { FieldFlag } from '../api/types';

/**
 * One BMS input field.
 *
 * <p>CSSETATY set the attribute byte of a field from its one byte edit flag: a field that failed an
 * edit was shown in red and a required field left blank additionally showed '*'. The React
 * equivalent marks the input invalid (which also announces it to assistive technology) and shows
 * the same asterisk placeholder, so the highlighting semantics survive the move off the terminal.
 */
export function Field({
  label,
  name,
  value,
  flag,
  readOnly,
  maxLength,
  size,
  onChange,
}: {
  label: string;
  name: string;
  value: string;
  flag?: FieldFlag;
  readOnly?: boolean;
  maxLength?: number;
  size?: number;
  onChange?: (value: string) => void;
}) {
  const invalid = flag === 'NOT_OK' || flag === 'BLANK';
  return (
    <input
      id={name}
      name={name}
      aria-label={label}
      value={value}
      readOnly={readOnly}
      aria-invalid={invalid || undefined}
      maxLength={maxLength}
      size={size ?? maxLength}
      placeholder={flag === 'BLANK' ? '*' : undefined}
      onChange={(event) => onChange?.(event.target.value)}
    />
  );
}

/** A label followed by one or more fields, the way the maps grouped date and phone parts. */
export function FieldRow({
  label,
  htmlFor,
  children,
}: {
  label: string;
  htmlFor?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="field">
      <label htmlFor={htmlFor}>{label}</label>
      <div className="field__group">{children}</div>
    </div>
  );
}
