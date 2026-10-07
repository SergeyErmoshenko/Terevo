import type { ReactNode } from "react";
import { Icon } from "../icons/Icon";

export function Dialog({
  eyebrow,
  title,
  subtitle,
  onClose,
  children,
  footer,
  width,
}: {
  eyebrow: string;
  title: string;
  subtitle?: string;
  onClose?: () => void;
  children: ReactNode;
  footer: ReactNode;
  width?: "sm" | "md" | "lg";
}) {
  return (
    <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose?.(); }}>
      <section className={`dialog dialog-${width ?? "md"}`} role="dialog" aria-modal="true">
        <header className="dialog-header">
          <div>
            <span className="eyebrow">{eyebrow}</span>
            <h2>{title}</h2>
            {subtitle && <p className="dialog-subtitle">{subtitle}</p>}
          </div>
          {onClose && (
            <button className="dialog-close" type="button" aria-label="Закрыть" onClick={onClose}>
              <Icon name="x" />
            </button>
          )}
        </header>
        <div className="dialog-body">{children}</div>
        <footer className="dialog-footer">{footer}</footer>
      </section>
    </div>
  );
}
