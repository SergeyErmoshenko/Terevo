import { useCallback, useEffect, useRef, useState } from "react";
import { emptyAppState, type AppStateDto } from "./dto";
import { getBridge } from "./client";
import type { DispatchableAction } from "./actions";

export interface Backend {
  state: AppStateDto;
  dispatch: (action: DispatchableAction) => Promise<AppStateDto | null>;
  busy: boolean;
  error: string;
  dismissError: () => void;
}

// The backend omits canvas.layout (sends null) whenever layoutVersion is unchanged from the
// last response, to avoid re-serializing a few hundred KB of nodes/edges on every action that
// doesn't touch the tree (selecting someone, opening a dialog, editing a date). Replacing state
// wholesale on every response would then wipe the tree off screen after the very first action.
// This keeps the last known non-null layout for as long as its version keeps matching.
function mergeResponseState(previous: AppStateDto, next: AppStateDto): AppStateDto {
  if (next.canvas.layout !== null) return next;
  if (next.canvas.layoutVersion !== previous.canvas.layoutVersion) return next;
  return { ...next, canvas: { ...next.canvas, layout: previous.canvas.layout } };
}

// One hook, one connection to the backend: holds the latest state, a dispatch function that
// returns the resulting state (so callers can chain, e.g. "save, then check canSave"), and a
// busy flag so forms can disable their submit button while a round trip is in flight.
export function useBackend(): Backend {
  const [state, setState] = useState<AppStateDto>(emptyAppState);
  const stateRef = useRef(state);
  stateRef.current = state;
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const idRef = useRef(0);

  const dispatch = useCallback(async (action: DispatchableAction): Promise<AppStateDto | null> => {
    const bridge = getBridge();
    if (!bridge) {
      setError("Откройте приложение через Electron, чтобы подключиться к базе проекта.");
      return null;
    }
    setBusy(true);
    setError("");
    try {
      const response = await bridge.dispatch(++idRef.current, action);
      let merged: AppStateDto | null = null;
      if (response?.state) {
        merged = mergeResponseState(stateRef.current, response.state);
        setState(merged);
      }
      if (response?.error) setError(response.error);
      return merged;
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Не удалось выполнить действие");
      return null;
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    const bridge = getBridge();
    if (!bridge) {
      setError("Откройте приложение через Electron, чтобы подключиться к базе проекта.");
      return;
    }
    bridge
      .start()
      .then((response) => {
        if (active && response?.state) setState(mergeResponseState(stateRef.current, response.state));
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : String(cause));
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    getBridge()?.reportMenuSummary({
      isProjectOpen: state.isProjectOpen,
      canUndo: state.canUndo,
      canRedo: state.canRedo,
      undoLabel: state.undoLabel,
      redoLabel: state.redoLabel,
      canGoBack: state.canGoBack,
      canGoForward: state.canGoForward,
      themeMode: state.themeMode,
      hasSelection: state.selectedPerson != null,
    });
  }, [
    state.isProjectOpen,
    state.canUndo,
    state.canRedo,
    state.undoLabel,
    state.redoLabel,
    state.canGoBack,
    state.canGoForward,
    state.themeMode,
    state.selectedPerson,
  ]);

  return { state, dispatch, busy, error, dismissError: () => setError("") };
}
