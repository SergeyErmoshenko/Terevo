import { useEffect, useRef, useState } from "react";
import type { Action } from "../../app/actions";
import { emptySearchFilter, isSearchFilterEmpty, type Gender, type SearchFilterDto } from "../../app/dto";
import type { AppStateDto } from "../../app/dto";
import { Icon } from "../icons/Icon";
import { SegmentedControl } from "../common/FormField";

const genderOptions: readonly (Gender | "ANY")[] = ["ANY", "MALE", "FEMALE", "UNKNOWN"];
const genderLabel: Record<Gender | "ANY", string> = { ANY: "Любой", MALE: "Мужской", FEMALE: "Женский", UNKNOWN: "Не указан" };
const tristateOptions: readonly ("ANY" | "YES" | "NO")[] = ["ANY", "YES", "NO"];

export function SearchBar({ state, dispatch }: { state: AppStateDto; dispatch: (action: Action) => void }) {
  const [filter, setFilter] = useState<SearchFilterDto>(state.searchFilter);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [resultsOpen, setResultsOpen] = useState(false);
  const debounceRef = useRef<number | null>(null);

  useEffect(() => setFilter(state.searchFilter), [state.searchFilter]);

  const commit = (next: SearchFilterDto, immediate = false) => {
    setFilter(next);
    if (debounceRef.current) window.clearTimeout(debounceRef.current);
    const send = () => dispatch({ type: "changeSearchFilter", filter: next });
    if (immediate) send();
    else debounceRef.current = window.setTimeout(send, 250);
  };

  const empty = isSearchFilterEmpty(filter);

  return (
    <div className="search-wrap">
      <label className="search-field">
        <Icon name="search" />
        <input
          value={filter.query}
          onChange={(event) => {
            commit({ ...filter, query: event.target.value });
            setResultsOpen(true);
          }}
          onFocus={() => setResultsOpen(true)}
          onBlur={() => window.setTimeout(() => setResultsOpen(false), 150)}
          placeholder="Найти человека"
          aria-label="Найти человека"
        />
        {filter.query && (
          <button className="search-clear" type="button" aria-label="Очистить поиск" onClick={() => commit({ ...filter, query: "" }, true)}>
            <Icon name="x" size={14} />
          </button>
        )}
      </label>
      <button
        className={`icon-button${!empty ? " filter-active" : ""}`}
        type="button"
        aria-label="Фильтры"
        onClick={() => setFiltersOpen((open) => !open)}
      >
        <Icon name="filter" />
      </button>

      {resultsOpen && state.searchResults.length > 0 && (
        <div className="search-results">
          {state.searchResults.map((person) => (
            <button
              key={person.id}
              type="button"
              onMouseDown={(event) => {
                event.preventDefault();
                setResultsOpen(false);
                dispatch({ type: "selectSearchResult", id: person.id });
              }}
            >
              <span>{person.name}</span>
              <small>{person.lifeYears}</small>
            </button>
          ))}
        </div>
      )}

      {filtersOpen && (
        <>
          <div className="context-menu-backdrop" onMouseDown={() => setFiltersOpen(false)} />
          <div className="filters-panel">
            <label className="field">
              <span className="field-label">Место</span>
              <input value={filter.place} onChange={(event) => commit({ ...filter, place: event.target.value })} />
            </label>
            <div className="field-row">
              <label className="field">
                <span className="field-label">Год рождения от</span>
                <input
                  type="number"
                  value={filter.birthYearFrom ?? ""}
                  onChange={(event) => commit({ ...filter, birthYearFrom: event.target.value ? Number(event.target.value) : null })}
                />
              </label>
              <label className="field">
                <span className="field-label">до</span>
                <input
                  type="number"
                  value={filter.birthYearTo ?? ""}
                  onChange={(event) => commit({ ...filter, birthYearTo: event.target.value ? Number(event.target.value) : null })}
                />
              </label>
            </div>
            <div className="field-row">
              <label className="field">
                <span className="field-label">Год смерти от</span>
                <input
                  type="number"
                  value={filter.deathYearFrom ?? ""}
                  onChange={(event) => commit({ ...filter, deathYearFrom: event.target.value ? Number(event.target.value) : null })}
                />
              </label>
              <label className="field">
                <span className="field-label">до</span>
                <input
                  type="number"
                  value={filter.deathYearTo ?? ""}
                  onChange={(event) => commit({ ...filter, deathYearTo: event.target.value ? Number(event.target.value) : null })}
                />
              </label>
            </div>
            <div className="field">
              <span className="field-label">Пол</span>
              <SegmentedControl
                options={genderOptions}
                selected={filter.gender ?? "ANY"}
                label={(value) => genderLabel[value]}
                onSelect={(value) => commit({ ...filter, gender: value === "ANY" ? null : value }, true)}
              />
            </div>
            <div className="field">
              <span className="field-label">Родители</span>
              <SegmentedControl
                options={tristateOptions}
                selected={filter.hasParents === null ? "ANY" : filter.hasParents ? "YES" : "NO"}
                label={(value) => (value === "ANY" ? "Любой" : value === "YES" ? "Есть родители" : "Нет родителей")}
                onSelect={(value) => commit({ ...filter, hasParents: value === "ANY" ? null : value === "YES" }, true)}
              />
            </div>
            <div className="field">
              <span className="field-label">Даты</span>
              <SegmentedControl
                options={tristateOptions}
                selected={filter.hasDates === null ? "ANY" : filter.hasDates ? "YES" : "NO"}
                label={(value) => (value === "ANY" ? "Любой" : value === "YES" ? "Есть даты" : "Нет дат")}
                onSelect={(value) => commit({ ...filter, hasDates: value === "ANY" ? null : value === "YES" }, true)}
              />
            </div>
            {!empty && (
              <button className="secondary-button full-width" type="button" onClick={() => commit(emptySearchFilter, true)}>
                <Icon name="filterOff" />
                Сбросить все фильтры
              </button>
            )}
          </div>
        </>
      )}
    </div>
  );
}
