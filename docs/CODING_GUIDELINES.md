# Terevo — Как писать код

Документ обязателен к соблюдению. Проверяется на ревью: линтеров и порогов покрытия в сборке намеренно нет, автоматически падают только тесты — включая архитектурный из T-002.

---

## 1. Код без комментариев

В `src/main` не должно быть ни одного комментария. Ни `//`, ни `/* */`, ни KDoc, ни TODO.

Комментарий — это признак того, что код не сумел объяснить себя сам. Вместо того чтобы дописать объяснение, нужно исправить код: переименовать, выделить функцию, ввести тип. Комментарии врут — они не компилируются, не тестируются и устаревают на первом же рефакторинге. Тест устареть не может: он падает.

Куда уходит то, что хотелось написать в комментарии:

| Хотелось написать | Куда девать |
|---|---|
| «Что делает этот блок» | Выделить в функцию с этим названием |
| «Что означает это число/строка» | Именованная константа или value class |
| «Какие значения тут допустимы» | Sealed-иерархия или enum вместо `String`/`Int` |
| «Что тут может пойти не так» | Тип результата `Outcome<T>` с явной ошибкой |
| «Почему выбран такой алгоритм» | `docs/*.md` со ссылкой на первоисточник |
| «Как этим пользоваться» | Тест с говорящим именем |
| «TODO доделать» | Задача в `TASKS.md`, а не в коде |

### Было

```kotlin
fun check(t: FamilyTree, p: PersonId, c: PersonId): Boolean {
    val seen = mutableSetOf<PersonId>()
    val queue = ArrayDeque<PersonId>()
    queue += p
    while (queue.isNotEmpty()) {
        val cur = queue.removeFirst()
        if (cur == c) return false
        if (!seen.add(cur)) continue
        queue += t.parentsOf[cur].orEmpty()
    }
    return true
}
```

### Стало

```kotlin
object NoCycles : Invariant {
    override fun check(tree: FamilyTree, change: Change): ValidationError? {
        val link = change as? Change.LinkParentChild ?: return null
        val ancestryOfParent = tree.ancestors(link.parent)
        return if (link.child in ancestryOfParent) {
            ValidationError.CycleDetected(tree.pathBetween(link.child, link.parent))
        } else {
            null
        }
    }
}
```

Читается сверху вниз одним предложением. Ни одного пояснения не требуется.

### Единственное исключение

Машиночитаемые аннотации, которые технически выглядят как комментарии (`// language=SQL` для подсветки в IDE, `@Suppress` — это аннотация, не комментарий) допустимы, если без них ломается инструментарий. Обычный текст — нет.

### Как это соблюдается

Автоматической проверки нет — правило держится на ревью. Grep находит нарушения за секунду:

```
grep -rn --include='*.kt' -E '(^|\s)(//|/\*)' src/main/kotlin
```

Относится к `src/main`. В тестах комментарий тоже нежелателен, но допустим.

---

## 2. Имена

* Функция — глагол или вопрос: `applyCommand`, `hasBiologicalParents`, `pathBetween`. Не `process`, не `handleData`, не `doWork`.
* Булево — предикат: `isAlive`, `canUndo`, `containsCycle`.
* Класс — существительное предметной области: `FamilyTree`, `MarriageStatus`, `LayoutOptions`. Не `Manager`, не `Helper`, не `Util`, не `Data`, не `Info`.
* Никаких сокращений, кроме общепринятых (`id`, `url`, `db`). `personRepository`, не `pRepo`.
* Тип в имени переменной не дублируется: `persons`, не `personList`.
* Длина имени пропорциональна области видимости: `it` в однострочной лямбде — норма, `t` в 40-строчной функции — нет.
* Именованные аргументы обязательны, если в вызове больше двух параметров одного типа: `Marriage(spouseA = a, spouseB = b)`.

---

## 3. Иммутабельность по умолчанию

* `val` везде. `var` допустим только внутри тела функции, в локальном цикле алгоритма (раскладка, обходы) — и никогда в полях класса домена.
* Доменные объекты — `data class` с `val`; изменение — `copy`.
* Коллекции в полях — `kotlinx.collections.immutable` (`PersistentList`, `PersistentMap`). Не `List` (за ним может стоять изменяемый `ArrayList`), тем более не `MutableList`.
* Мутабельные коллекции живут не дольше одной функции: собрали внутри — вернули immutable наружу.

```kotlin
fun FamilyTree.withPerson(person: Person): FamilyTree =
    copy(
        persons = persons.put(person.id, person),
        index = index.afterPersonAdded(person),
    )
```

---

## 4. Делать невалидное состояние непредставимым

Это главный принцип модели. Если объект существует — он валиден. Проверять его повторно не нужно нигде.

**Value class вместо примитива.** `PersonId` нельзя перепутать с `RelationId`, компилятор не даст. При этом на рантайме это тот же `Uuid` — оверхеда нет.

```kotlin
@JvmInline
value class PersonId(val value: Uuid)
```

**Sealed вместо флагов и nullable-полей.** Не `date: LocalDate?, isApproximate: Boolean, dateTo: LocalDate?` — четыре невозможные комбинации из восьми. А:

```kotlin
sealed interface EventDate {
    data class Exact(val date: LocalDate) : EventDate
    data class Approximate(val around: LocalDate, val precision: Precision) : EventDate
    data class Range(val from: LocalDate, val to: LocalDate) : EventDate
    data object Unknown : EventDate
}
```

**Smart-конструктор.** Публичного конструктора у сущностей с правилами нет:

```kotlin
class LifeSpan private constructor(
    val birth: EventDate,
    val death: EventDate,
) {
    companion object {
        operator fun invoke(birth: EventDate, death: EventDate): Outcome<LifeSpan> =
            if (death.startsBefore(birth)) {
                Outcome.Err(DomainError.InvalidDate.DeathBeforeBirth(birth, death))
            } else {
                Outcome.Ok(LifeSpan(birth, death))
            }
    }
}
```

**`when` без `else`.** По sealed-иерархии всегда исчерпывающий `when` без ветки `else` — добавление нового наследника обязано ломать компиляцию во всех местах, где его забыли учесть. `else` в таком `when` — это отложенный баг.

---

## 5. Ошибки — это значения, а не исключения

```kotlin
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Err(val error: DomainError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Ok -> Outcome.Ok(transform(value))
    is Outcome.Err -> this
}

inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Ok -> transform(value)
    is Outcome.Err -> this
}
```

Правила:

* В `domain` нет ни одного `throw`. Ожидаемый провал — часть сигнатуры, а не сюрприз в рантайме.
* `try/catch` живёт только в адаптерах (`persistence`, `gedcom`, работа с файлами) и немедленно конвертирует исключение в `DomainError.StorageFailure`.
* Ловить `Exception` целиком запрещено; ловится конкретный тип. `CancellationException` пробрасывается всегда.
* `!!` запрещён. Нет валидного случая его применения в этом проекте.
* `?:` с осмысленным значением по умолчанию или ранний возврат — вместо цепочек `?.`.

---

## 6. Зависимости — через конструктор, сборка — в одном месте

DI-фреймворка нет. Он не нужен приложению из шести пакетов и стоит дороже, чем даёт.

```kotlin
class CommandBus(
    private val repository: TreeRepository,
    private val invariants: List<Invariant>,
    private val undoLimit: Int,
)
```

Всё дерево объектов собирается в `me.terevo.app`:

```kotlin
class AppComponent(projectPath: Path) {
    private val database = TerevoDatabase.open(projectPath)
    private val repository: TreeRepository = SqlDelightTreeRepository(database)
    private val commandBus = CommandBus(repository, Invariants.all, UNDO_LIMIT)
    val treeStore = TreeStore(commandBus, LayoutEngine())
}
```

* Никаких `object`-синглтонов с состоянием, никакого глобального `Locator`, никакой инъекции через поля.
* `object` допустим только для чистых stateless-наборов функций и констант (`Invariants`, `PersonMapper`).
* Интерфейс объявляется там, где он **используется** (`domain.port`), а реализуется там, где есть технология (`persistence`). Это и есть инверсия зависимостей — стрелка компиляции идёт против стрелки вызова.
* Интерфейс заводится, когда есть вторая реализация или её нужно подменить в тестах. Интерфейс с единственной реализацией `FooImpl` — мусор.

---

## 7. Команда как единица изменения

Всё, что меняет дерево, проходит через `Command`. Никакой другой путь к репозиторию из UI не существует.

```kotlin
sealed interface Command {
    fun applyTo(tree: FamilyTree): Outcome<CommandResult>
}

data class CommandResult(
    val tree: FamilyTree,
    val inverse: Command,
    val warnings: List<ValidationWarning>,
)

data class UpdatePerson(val person: Person) : Command {
    override fun applyTo(tree: FamilyTree): Outcome<CommandResult> {
        val previous = tree.person(person.id) ?: return Outcome.Err(DomainError.PersonNotFound(person.id))
        return Outcome.Ok(
            CommandResult(
                tree = tree.withPerson(person),
                inverse = UpdatePerson(previous),
                warnings = Invariants.warningsFor(tree, person),
            ),
        )
    }
}
```

Инверсия строится в момент применения — только там ещё доступно предыдущее состояние. Undo/redo из этого получается бесплатно, а не как отдельная подсистема.

---

## 8. UI: однонаправленный поток

```kotlin
class TreeStore(
    private val commandBus: CommandBus,
    private val layoutEngine: LayoutEngine,
    scope: CoroutineScope,
) : Store<TreeState, TreeIntent>(TreeState.Empty, scope) {

    override suspend fun handle(intent: TreeIntent, state: TreeState): TreeState = when (intent) {
        is TreeIntent.SelectPerson -> state.copy(selected = intent.id)
        is TreeIntent.DeletePerson -> execute(RemovePerson(intent.id), state)
        is TreeIntent.Undo -> applyTree(commandBus.undo(), state)
        is TreeIntent.ZoomBy -> state.copy(camera = state.camera.zoomedBy(intent.factor, intent.pivot))
    }
}
```

Правила Compose:

* Composable-функции не создают Store, не открывают корутины на бизнес-логику и ничего не мутируют вне `remember`. Они принимают `state` и `onIntent`.
* Состояние экрана — один `data class` со `val`. Производные значения (`canUndo`, `visibleNodes`) вычисляются при построении состояния, а не в теле composable.
* Тяжёлое вычисление внутри композиции — только через `remember(key)` / `derivedStateOf`. Раскладка считается вне композиции, в `Dispatchers.Default`.
* Побочные эффекты — только в `LaunchedEffect`/`DisposableEffect` с явным ключом.
* Списки — с `key = { it.id }`, иначе Compose пересоздаёт узлы при любой перестановке.
* Все параметры composable — стабильные типы (`data class` с `val`, immutable-коллекции). `List<T>` считается нестабильным и убивает пропуск рекомпозиции.
* `Modifier` — всегда первый необязательный параметр и всегда пробрасывается наружу.
* Ноль магических чисел: отступы из `TerevoSpacing`, цвета из темы.

---

## 9. Алгоритмы — чистые функции

`layout`, калькулятор родства, статистика — функции без состояния, без ввода-вывода, без времени.

```kotlin
fun interface LayoutEngine {
    fun layout(request: LayoutRequest): Layout
}
```

* Результат зависит только от аргументов. Одинаковый вход → бит-в-бит одинаковый выход. Иначе golden-тесты невозможны.
* Никаких `System.currentTimeMillis()`, `Random` без seed, чтения файлов внутри алгоритма.
* Рекурсия по данным пользователя запрещена — обходы графа только явным стеком/очередью. 20 поколений — нормально, 10 000 узлов в цепочке — `StackOverflowError`.
* Алгоритмическая сложность указывается в имени теста, а не в комментарии: `layout_of_ten_thousand_nodes_completes_under_500ms`.

---

## 10. Производительность

* Индексы вместо перебора: `childrenOf[id]` вместо `relations.filter { it.parent == id }`. Второе на 10 000 людях превращает любой обход в квадрат.
* Последовательности (`asSequence()`) для цепочек преобразований по большим коллекциям — чтобы не плодить промежуточные списки.
* Предвыделение размера: `ArrayList(expectedSize)`, `HashMap(expectedSize)` в горячих местах.
* Никакой рефлексии, никакого `Class.forName`. Медленно на старте и ломается при обфускации.
* Оптимизация только после замера. Каждое ускорение сопровождается бенчмарком из T-080, иначе это догадка.

---

## 11. Корутины

* Structured concurrency: у каждого компонента свой scope, отменяемый вместе с ним. `GlobalScope` запрещён.
* Диспетчеры: `Dispatchers.IO` — файлы и SQLite, `Dispatchers.Default` — раскладка и расчёты, `Dispatchers.Main` — только UI-состояние.
* Диспетчер выбирает вызывающий код, `suspend`-функция сама переключает контекст через `withContext` и не блокирует поток.
* `runBlocking` — только в `main()` и тестах.
* Общее мутируемое состояние — через `StateFlow`/`MutableStateFlow`, не через `synchronized` и не через `@Volatile`.

---

## 12. Тесты

* Именование: `метод_условие_результат` в бэктиках или snake_case: `` `addRelation returns CycleDetected when child is ancestor of parent` ``.
* Структура given / when / then — пустой строкой, без заголовков-комментариев.
* Один тест — одно утверждение о поведении. Пять `assertEquals` подряд по разным аспектам — пять тестов.
* Тестовые данные — через билдеры в `me.terevo.testing`: `person(name = "Иван") { born(1900) }`. Ни одного `Person(...)` с десятью аргументами в теле теста.
* Property-based (kotest-property) для инвариантов домена: цикличность, undo/redo, консистентность индексов — там, где перебор случаев руками бесперспективен.
* Golden-тесты для раскладки: эталон в `src/test/resources/golden/*.json`, обновление только осознанным перезапуском с флагом.
* Тестируется поведение через публичный API слоя. Приватные функции не тестируются — если очень хочется, значит, там прячется отдельный класс.
* Моки не используются: есть `InMemoryTreeRepository` и другие фейки. Мок-фреймворк тестирует вызовы, а фейк — поведение.

---

## 13. Запрещено

| Запрет | Почему |
|---|---|
| Комментарии в `src/main` | См. §1 |
| `!!` | Скрытый `NullPointerException` |
| `lateinit` в домене | Объект существует в невалидном состоянии |
| `var` в полях доменных классов | Ломает предсказуемость и потокобезопасность |
| `else` в `when` по sealed | Новый вариант молча провалится в дефолт |
| `throw` в `domain` | Ошибка обязана быть в сигнатуре |
| `catch (e: Exception)` | Проглатывает в том числе отмену корутины |
| `GlobalScope` | Утечка корутин, неотменяемая работа |
| `Utils`, `Helper`, `Manager`, `*Impl` | Имена, не несущие смысла |
| Синглтоны с состоянием | Скрытая зависимость, невоспроизводимые тесты |
| Магические числа и строковые литералы в UI | Константы и тема |
| Рефлексия | Скорость старта, хрупкость |
| `TODO`/`FIXME` | Задача заводится в `TASKS.md` |
| Compose в `domain` и `layout` | Разрушает тестируемость и разделение слоёв |
| Импорт `ui`/`persistence` из `domain` | Инверсия зависимостей наоборот; ловится тестом T-002 |

---

## 14. Ритуал коммита

1. `./gradlew check` — зелёный.
2. Новое поведение покрыто тестом; исправленный баг покрыт тестом, который падал до правки.
3. Изменения в структуре пакетов или в контрактах портов отражены в `docs/ARCHITECTURE.md`.
4. Один коммит — одна задача из `TASKS.md`, префикс `T-0NN: `.
5. Форматирование — `reformat_file` в IDE, не руками.
