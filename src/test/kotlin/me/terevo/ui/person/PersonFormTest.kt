package me.terevo.ui.person

import kotlinx.datetime.LocalDate
import me.terevo.domain.Outcome
import me.terevo.domain.command.CommandBus
import me.terevo.domain.model.DatePrecision
import me.terevo.domain.model.EventDate
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.Gender
import me.terevo.testing.InMemoryTreeRepository
import kotlin.test.*

class PersonFormTest {

    @Test
    fun `exact approximate range and unknown dates map to domain`() {
        val first = listOf("02", "01", "2000").joinToString(".")
        val second = listOf("04", "03", "2001").joinToString(".")
        val states = listOf(
            EventDateInput(EventDateMode.EXACT, first),
            EventDateInput(EventDateMode.APPROXIMATE, first, precision = DatePrecision.YEAR),
            EventDateInput(EventDateMode.RANGE, first, second),
            EventDateInput(),
        )

        val dates = states.map { input ->
            val person = PersonFormState(surname = "Иванов", birth = input).toPerson()
            assertIs<Outcome.Ok<*>>(person)
            (person as Outcome.Ok).value.lifeSpan.birth
        }

        assertEquals(EventDate.Exact(LocalDate(2000, 1, 2)), dates[0])
        assertEquals(EventDate.Approximate(LocalDate(2000, 1, 2), DatePrecision.YEAR), dates[1])
        assertIs<EventDate.Range>(dates[2])
        assertEquals(EventDate.Unknown, dates[3])
    }

    @Test
    fun `alive person ignores stale death date and place`() {
        val state = PersonFormState(
            surname = "Иванов",
            isAlive = true,
            death = EventDateInput(EventDateMode.EXACT, listOf("02", "01", "2020").joinToString(".")),
            deathPlace = "Москва",
        )

        val person = (state.toPerson() as Outcome.Ok).value

        assertTrue(person.isAlive)
        assertEquals(EventDate.Unknown, person.lifeSpan.death)
        assertEquals(null, person.deathPlace)
    }

    @Test
    fun `deceased person maps death date and place`() {
        val state = PersonFormState(
            surname = "Иванов",
            isAlive = false,
            death = EventDateInput(EventDateMode.EXACT, listOf("02", "01", "2020").joinToString(".")),
            deathPlace = "Москва",
        )

        val person = (state.toPerson() as Outcome.Ok).value

        assertFalse(person.isAlive)
        assertEquals(EventDate.Exact(LocalDate(2020, 1, 2)), person.lifeSpan.death)
        assertEquals("Москва", person.deathPlace?.title)
        assertFalse(PersonFormState.fromPerson(person).isAlive)
    }

    @Test
    fun `validation preserves uppercase letters in full name`() {
        val state = validatePersonForm(PersonFormState(surname = "ИВАНОВ", givenName = "Иван"))

        val person = (state.toPerson() as Outcome.Ok).value

        assertEquals("ИВАНОВ", state.surname)
        assertEquals("ИВАНОВ Иван", person.name.display)
    }

    @Test
    fun `form maps maiden name only for female gender`() {
        val female = (PersonFormState(
            surname = "Иванова",
            gender = Gender.FEMALE,
            maidenName = "Петрова",
        ).toPerson() as Outcome.Ok).value
        val male = (PersonFormState(
            surname = "Иванов",
            gender = Gender.MALE,
            maidenName = "Петров",
        ).toPerson() as Outcome.Ok).value

        assertEquals("Петрова", female.name.maidenName)
        assertEquals("", male.name.maidenName)
    }

    @Test
    fun `custom fields map to domain and survive edit form round trip`() {
        val state = PersonFormState(
            surname = "Иванов",
            customFields = listOf(CustomFieldInput("Профессия", "врач"), CustomFieldInput()),
        )

        val person = (state.toPerson() as Outcome.Ok).value
        val restored = PersonFormState.fromPerson(person, listOf("Профессия", "Награды"))

        assertEquals(mapOf("Профессия" to "врач"), person.customFields)
        assertEquals(listOf(CustomFieldInput("Профессия", "врач")), restored.customFields)
        assertEquals(listOf("Профессия", "Награды"), restored.customFieldSuggestions)
    }

    @Test
    fun `residence and occupation map to domain and survive edit form round trip`() {
        val state = PersonFormState(surname = "Иванов", residence = "Казань", occupation = "врач")

        val person = (state.toPerson() as Outcome.Ok).value
        val restored = PersonFormState.fromPerson(person)

        assertEquals("Казань", person.residence?.title)
        assertEquals("врач", person.occupation)
        assertEquals("Казань", restored.residence)
        assertEquals("врач", restored.occupation)
    }

    @Test
    fun `pending media paths make an otherwise unchanged form dirty`() {
        val person = (PersonFormState(surname = "Иванов").toPerson() as Outcome.Ok).value
        val restored = PersonFormState.fromPerson(person)

        assertFalse(restored.isDirty)
        assertTrue(restored.copy(pendingMediaPaths = listOf("/tmp/photo.txt")).isDirty)
    }

    @Test
    fun `custom field keys and values are normalized`() {
        val state = PersonFormState(
            surname = "Иванов",
            customFields = listOf(CustomFieldInput("  Профессия  ", "  врач  ")),
        )

        val person = (state.toPerson() as Outcome.Ok).value

        assertEquals(mapOf("Профессия" to "врач"), person.customFields)
    }

    @Test
    fun `duplicate custom field keys block saving`() {
        val state = validatePersonForm(
            PersonFormState(
                surname = "Иванов",
                customFields = listOf(CustomFieldInput("Профессия", "врач"), CustomFieldInput("Профессия", "учитель")),
            ),
        )

        assertFalse(state.canSave)
        assertEquals("Укажите уникальное название дополнительного поля", state.blockingError)
    }

    @Test
    fun `live validation blocks blank name`() {
        val state = validatePersonForm(PersonFormState())

        assertFalse(state.canSave)
        assertEquals("Укажите фамилию или имя", state.blockingError)
    }

    @Test
    fun `invalid date has dedicated format message`() {
        val state = validatePersonForm(
            PersonFormState(surname = "Иванов", birth = EventDateInput(EventDateMode.EXACT, "1980")),
        )

        assertFalse(state.canSave)
        assertEquals("Введите дату в формате ДД.ММ.ГГГГ", state.blockingError)
    }

    @Test
    fun `dirty cancel requests confirmation`() {
        val state = PersonFormState(surname = "Иванов")

        assertTrue(PersonFormService(CommandBus()).requestCancel(state).isDiscardConfirmationVisible)
    }

    @Test
    fun `add update and delete travel through command bus repository`() {
        val repository = InMemoryTreeRepository(FamilyTree.EMPTY)
        val bus = CommandBus(repository = repository)
        val service = PersonFormService(bus)
        val create = PersonFormState(surname = "Иванов", givenName = "Иван", notes = "Заметка")

        assertIs<Outcome.Ok<*>>(service.save(create))
        val created = assertNotNull(bus.tree.value.person(create.id))
        val edit = PersonFormState.fromPerson(created).copy(givenName = "Пётр")
        assertIs<Outcome.Ok<*>>(service.save(edit))
        assertEquals("Пётр", bus.tree.value.person(create.id)?.name?.givenName)
        assertIs<Outcome.Ok<*>>(service.delete(edit))
        assertFalse(bus.tree.value.contains(create.id))
        assertEquals(bus.tree.value, repository.load().let { (it as Outcome.Ok).value })
    }
}
