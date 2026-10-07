import type { Action } from "../../app/actions";
import type { DragRelationMenuDto, RelationMode } from "../../app/dto";

export function DragRelationMenuPopup({ menu, dispatch }: { menu: DragRelationMenuDto; dispatch: (action: Action) => void }) {
  const spouseLabel = menu.sourceGender === "MALE" ? "Добавить супругу" : "Добавить супруга";
  const item = (mode: RelationMode, label: string) => (
    <button type="button" disabled={!menu.validity[mode]} onClick={() => dispatch({ type: "chooseDragRelationMode", mode })}>
      {label}
    </button>
  );
  return (
    <>
      <div className="context-menu-backdrop" onMouseDown={() => dispatch({ type: "cancelDragRelationMenu" })} />
      <div className="context-menu" style={{ left: menu.x, top: menu.y }}>
        {item("PARENT", "Сделать родителем")}
        {item("CHILD", "Сделать ребёнком")}
        {item("SPOUSE", spouseLabel)}
      </div>
    </>
  );
}
