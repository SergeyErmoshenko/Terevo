import {Icon} from "../icons/Icon";

// Search for the table tabs: it only narrows the rows of the table on screen and never touches
// the tree's own search/highlight state.
export function TableSearch({value, placeholder, onChange}: {
    value: string;
    placeholder: string;
    onChange: (value: string) => void
}) {
    return (
        <div className="search-wrap">
            <label className="search-field">
                <Icon name="search"/>
                <input value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder}
                       aria-label={placeholder}/>
                {value && (
                    <button className="search-clear" type="button" aria-label="Очистить поиск"
                            onClick={() => onChange("")}>
                        <Icon name="x" size={14}/>
                    </button>
                )}
            </label>
        </div>
    );
}

// Same rule as the tree search: every whitespace-separated word has to occur somewhere in the text.
export function matchesQuery(query: string, ...fields: (string | null | undefined)[]): boolean {
    const terms = query.trim().toLocaleLowerCase("ru").split(/\s+/).filter(Boolean);
    if (terms.length === 0) return true;
    const text = fields.join(" ").toLocaleLowerCase("ru");
    return terms.every((term) => text.includes(term));
}
