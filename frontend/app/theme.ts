import { useEffect, useState } from "react";
import type { ThemeMode } from "./dto";

export type EffectiveTheme = "light" | "dark";

function systemTheme(): EffectiveTheme {
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

// SYSTEM tracks the OS setting live, including a change while the app is open.
export function useEffectiveTheme(mode: ThemeMode): EffectiveTheme {
  const [system, setSystem] = useState<EffectiveTheme>(systemTheme);

  useEffect(() => {
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const onChange = () => setSystem(media.matches ? "dark" : "light");
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, []);

  const effective = mode === "SYSTEM" ? system : mode === "DARK" ? "dark" : "light";

  // The theme class must live on <html>, not on a div inside <body>: CSS custom properties
  // resolve top-down in the DOM, and body { color: var(--color-text) } in styles.css sits above
  // that div, so it would always resolve against :root's (dark) values regardless of which
  // theme class the div itself carried. Every element that just inherits color instead of
  // setting its own (table cells, plain text) stayed unreadable in light mode no matter what.
  useEffect(() => {
    document.documentElement.classList.toggle("theme-light", effective === "light");
    document.documentElement.classList.toggle("theme-dark", effective === "dark");
  }, [effective]);

  return effective;
}
