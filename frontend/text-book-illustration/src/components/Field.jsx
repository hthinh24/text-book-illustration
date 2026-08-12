import React from 'react';

export function Field({
  label,
  id,
  type = 'text',
  value,
  onChange,
  placeholder,
  required = false,
  error,
  disabled = false,
  multiline = false,
  rows = 3,
  className = '',
  ...props
}) {
  return (
    <div className={`gd-field ${className}`.trim()}>
      {label && (
        <label htmlFor={id}>
          {label}
          {required && <span className="req">*</span>}
        </label>
      )}
      {multiline ? (
        <textarea
          id={id}
          value={value}
          onChange={onChange}
          placeholder={placeholder}
          required={required}
          disabled={disabled}
          rows={rows}
          {...props}
        />
      ) : (
        <input
          id={id}
          type={type}
          value={value}
          onChange={onChange}
          placeholder={placeholder}
          required={required}
          disabled={disabled}
          {...props}
        />
      )}
      {error && <div className="gd-field-error">{error}</div>}
    </div>
  );
}
