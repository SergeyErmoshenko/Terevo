import type { Action } from "../../app/actions";
import type { AppStateDto, LayoutDensity, LayoutDirection, LayoutMode } from "../../app/dto";

const modeLabel: Record<LayoutMode, string> = {
  WHOLE_FAMILY: "Вся семья",
  ANCESTORS: "Предки",
  DESCENDANTS: "Потомки",
  BOTH: "Оба",
};
const directionLabel: Record<LayoutDirection, string> = { TOP_DOWN: "Сверху вниз", LEFT_RIGHT: "Слева направо" };
const densityLabel: Record<LayoutDensity, string> = { COMPACT: "Компактно", SPACIOUS: "Просторно" };

export function LayoutControls({ state, dispatch }: { state: AppStateDto; dispatch: (action: Action) => void }) {
  return (
    <>
      <select aria-label="Режим древа" value={state.layoutMode} onChange={(event) => dispatch({ type: "changeLayoutMode", mode: event.target.value as LayoutMode })}>
        {(Object.keys(modeLabel) as LayoutMode[]).map((mode) => (
          <option key={mode} value={mode}>
            {modeLabel[mode]}
          </option>
        ))}
      </select>
      <select aria-label="Направление древа" value={state.layoutDirection} onChange={(event) => dispatch({ type: "changeLayoutDirection", direction: event.target.value as LayoutDirection })}>
        {(Object.keys(directionLabel) as LayoutDirection[]).map((direction) => (
          <option key={direction} value={direction}>
            {directionLabel[direction]}
          </option>
        ))}
      </select>
      <select aria-label="Плотность" value={state.layoutDensity} onChange={(event) => dispatch({ type: "changeLayoutDensity", density: event.target.value as LayoutDensity })}>
        {(Object.keys(densityLabel) as LayoutDensity[]).map((density) => (
          <option key={density} value={density}>
            {densityLabel[density]}
          </option>
        ))}
      </select>
      {state.layoutMode !== "WHOLE_FAMILY" && (
        <label className="depth-field" title="Глубина">
          <input
            type="number"
            min={1}
            value={state.layoutDepth ?? ""}
            placeholder="∞"
            onChange={(event) => dispatch({ type: "changeLayoutDepth", depth: event.target.value ? Number(event.target.value) : null })}
          />
        </label>
      )}
    </>
  );
}
