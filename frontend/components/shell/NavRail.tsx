import type { Action } from "../../app/actions";
import type { MainTab } from "../../app/dto";
import { Icon, type IconName } from "../icons/Icon";

const tabs: { tab: MainTab; icon: IconName; label: string }[] = [
  { tab: "TREE", icon: "tree", label: "Дерево" },
  { tab: "PERSONS", icon: "people", label: "Люди" },
  { tab: "EVENTS", icon: "events", label: "События" },
  { tab: "DOCUMENTS", icon: "documents", label: "Документы" },
];

export function NavRail({ active, dispatch, onStatistics }: { active: MainTab; dispatch: (action: Action) => void; onStatistics: () => void }) {
  return (
    <aside className="sidebar">
      <nav className="nav-list" aria-label="Разделы проекта">
        {tabs.map(({ tab, icon, label }) => (
          <button key={tab} type="button" className={`nav-item${tab === active ? " active" : ""}`} onClick={() => dispatch({ type: "changeMainTab", tab })}>
            <span className="nav-icon">
              <Icon name={icon} />
            </span>
            <span>{label}</span>
          </button>
        ))}
      </nav>
      <div className="sidebar-footer">
        <button className="nav-item" type="button" onClick={onStatistics}>
          <span className="nav-icon">
            <Icon name="stats" />
          </span>
          <span>Статистика</span>
        </button>
      </div>
    </aside>
  );
}
