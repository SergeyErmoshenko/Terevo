import type { Action } from "../../app/actions";
import type { NamedCountDto, StatisticsDto } from "../../app/dto";
import { Dialog } from "./Dialog";

function BarChart({ title, items }: { title: string; items: NamedCountDto[] }) {
  const max = Math.max(1, ...items.map((item) => item.count));
  return (
    <div className="bar-chart">
      <h3>{title}</h3>
      {items.length === 0 && <p className="field-hint">Нет данных</p>}
      {items.map((item) => (
        <div className="bar-row" key={item.name}>
          <span className="bar-label" title={item.name}>
            {item.name}
          </span>
          <div className="bar-track">
            <div className="bar-fill" style={{ width: `${(item.count / max) * 100}%` }} />
          </div>
          <span className="bar-value">{item.count}</span>
        </div>
      ))}
    </div>
  );
}

function GenerationChart({ distribution }: { distribution: Record<string, number> }) {
  const entries = Object.entries(distribution)
    .map(([generation, count]) => [Number(generation), count] as const)
    .sort((a, b) => a[0] - b[0]);
  const max = Math.max(1, ...entries.map(([, count]) => count));
  return (
    <div className="bar-chart">
      <h3>Распределение по поколениям</h3>
      {entries.map(([generation, count]) => (
        <div className="bar-row" key={generation}>
          <span className="bar-label">Поколение {generation + 1}</span>
          <div className="bar-track">
            <div className="bar-fill" style={{ width: `${(count / max) * 100}%` }} />
          </div>
          <span className="bar-value">{count}</span>
        </div>
      ))}
    </div>
  );
}

export function StatisticsDialog({ statistics, dispatch }: { statistics: StatisticsDto; dispatch: (action: Action) => void }) {
  return (
    <Dialog
      eyebrow="ОБЗОР ПРОЕКТА"
      title="Статистика"
      onClose={() => dispatch({ type: "closeStatistics" })}
      width="lg"
      footer={
        <button className="secondary-button" type="button" onClick={() => dispatch({ type: "closeStatistics" })}>
          Закрыть
        </button>
      }
    >
      <div className="stats-grid">
        <Stat label="Всего людей" value={statistics.totalPersons} />
        <Stat label="Живы" value={statistics.livingCount} />
        <Stat label="Умерли" value={statistics.deceasedCount} />
        <Stat
          label="Средняя продолжительность жизни"
          value={statistics.averageLifespanYears == null ? "—" : `${statistics.averageLifespanYears.toFixed(1)} лет`}
        />
        <Stat label="Без даты рождения" value={statistics.missingBirthDateCount} />
        <Stat label="Без указанных родителей" value={statistics.missingParentsCount} />
      </div>
      <GenerationChart distribution={statistics.generationDistribution} />
      <BarChart title="Самые частые фамилии" items={statistics.topSurnames} />
      <BarChart title="Самые частые места" items={statistics.topPlaces} />
    </Dialog>
  );
}

function Stat({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="stat-item">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
