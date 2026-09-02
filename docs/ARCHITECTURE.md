# Terevo — Архитектура

## Стек

| Слой | Технология |
|---|---|
| Язык | Kotlin 2.4, JDK 21 |
| Сборка | Gradle 9.6 (Kotlin DSL, version catalog) |
| UI | Compose Multiplatform Desktop |
| Хранилище | SQLite (WAL) + SQLDelight |
| Асинхронность | kotlinx.coroutines, `Flow` / `StateFlow` |
| Сериализация | kotlinx.serialization (GEDCOM/JSON-обмен, настройки) |
| Тесты | kotlin.test + JUnit5, kotest-property (property-based) |

## Структура пакетов

Проект — один Gradle-модуль. Слои разделены пакетами, границы между ними проверяются тестом, а не дисциплиной.

```
me.terevo
├── domain          модель, инварианты, команды, undo/redo, порты (интерфейсы)
│   ├── model
│   ├── invariant
│   ├── command
│   └── port
├── persistence     SQLDelight-адаптеры портов, миграции, файл проекта
├── layout          чистый алгоритм раскладки дерева
├── kinship         чистый алгоритм определения родства (термины на русском)
├── statistics      чистый расчёт статистики по дереву (счётчики, поколения, топы)
├── gedcom          парсер/сериализатор GEDCOM 5.5.1
├── export          пагинация и рендер дерева в PDF (PDFBox), без Compose
├── ui              Compose-экраны, Store (MVI), рендер дерева
│   ├── tree
│   ├── person
│   ├── kinship
│   ├── statistics
│   ├── export
│   └── theme
└── app             composition root, main(), сборка дистрибутивов
```

Граф зависимостей (стрелка = "зависит от"):

```
app ──► ui ──► domain ◄── persistence
 │      │                    │
 │      ├───► layout         │
 │      ├───► kinship ──► domain
 │      └───► statistics ──► domain
 ├────────────────────────► gedcom ──► domain
 └────────────────────────► export ──► domain
                             export ──► layout
```

Правила:

* `domain` не импортирует ничего, кроме stdlib, coroutines и immutable-коллекций. Ни Compose, ни SQL, ни файловой системы.
* `layout` не импортирует Compose: на входе — граф и размеры узлов, на выходе — координаты.
* `kinship` не импортирует ничего, кроме stdlib и `domain`: на входе — `FamilyTree` и два `PersonId`, на выходе — термин родства.
* `statistics` не импортирует ничего, кроме stdlib и `domain`: на входе — `FamilyTree`, на выходе — снимок статистики.
* `persistence`, `gedcom` и `export` реализуют интерфейсы (порты), объявленные в `domain`, и не зависят от `ui`/`app`/Compose. `export` дополнительно зависит от `layout` (использует `Layout`/`Rect`/`NodeId` для пагинации и геометрии PDF-страниц) и PDFBox.
* PNG-экспорт (`ui.export`) — часть UI-слоя, а не `export`: он переиспользует Compose-функцию `ui.tree.drawTree` напрямую через offscreen `ImageComposeScene`, поэтому не может лежать в Compose-независимом `export`.
* `app` — единственное место, где создаются реальные реализации и связываются друг с другом.
* Обратных зависимостей нет: `domain` не знает про `ui`, `persistence`, `layout`, `kinship`, `statistics`, `gedcom`, `export`.

Это ports & adapters: домен диктует контракт, инфраструктура подстраивается. Заменить SQLite на что-то другое — значит написать новый пакет и поменять 10 строк в `app`.

Правила выше проверяются Konsist-тестом (`ArchitectureTest`), который падает при первом же неправильном импорте. Разделение на Gradle-модули отложено до появления конкретного повода — см. T-005.

## Поток данных

```
Пользователь → Intent → Store.dispatch
                          │
                          ├─► CommandBus.execute(Command)   мутации
                          │        └─► Repository (транзакция) → SQLite
                          │        └─► UndoStack.push(inverse)
                          │
                          └─► reduce(state, event) → StateFlow<UiState> → Compose
```

Одно направление, один источник истины на экран. Compose только читает `StateFlow` и шлёт `Intent`. Никакой мутируемой логики в composable-функциях.

## Ключевые решения

**Проект — переносимый каталог в документах пользователя.** По умолчанию Terevo создаёт `Documents/Terevo/<project>/` с базой `project.terevo`, каталогами `backups/`, `media/` и `thumbnails/`. Открытие проекта = открытие SQLite-файла внутри каталога. Автосохранение — побочный эффект транзакций, "Сохранить как" = создание нового проектного каталога через `VACUUM INTO`. Медиа адресуются по SHA-256 и хранятся внутри `media/`; в БД лежат метаданные, хэши и привязки. Бэкапы создаются через `VACUUM INTO` внутри `backups/` и не включают производный кэш миниатюр.

**Домен целиком в памяти, БД — источник истины.** 10 000 человек + связи — это единицы мегабайт. `FamilyTree` держит индексы (`parentsOf`, `childrenOf`, `spousesOf`) как `Map<PersonId, PersistentList<PersonId>>`. Все проверки инвариантов и раскладка работают по памяти, БД пишется транзакционно на каждую команду.

**Undo/redo через инвертируемые команды.** У каждой команды есть `inverse(tree): Command`. Стек хранит команды, а не снапшоты — память не растёт. Там, где инверсия неочевидна (импорт GEDCOM), команда сохраняет memento-снапшот затронутого подмножества.

**Раскладка — чистая функция.** `layout(graph, metrics): Layout`. Это делает её тестируемой golden-тестами и позволяет считать в фоновом диспетчере, не трогая UI-поток.

## Целевые показатели

* 10 000 человек: открытие проекта < 2 с, полная раскладка < 500 мс, инкрементальная < 50 мс.
* Отклик UI на основные действия < 100 мс (p95).
* Потеря данных при `kill -9` = 0 подтверждённых команд.
