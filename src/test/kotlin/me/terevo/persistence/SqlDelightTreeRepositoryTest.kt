package me.terevo.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.datetime.LocalDate
import me.terevo.domain.command.*
import me.terevo.domain.model.*
import me.terevo.persistence.db.TerevoDatabase
import me.terevo.testing.name
import me.terevo.testing.person
import me.terevo.testing.shouldBeOk
import kotlin.test.*

class SqlDelightTreeRepositoryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var repository: SqlDelightTreeRepository

    @BeforeTest
    fun open() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TerevoDatabase.Schema.create(driver)
        repository = SqlDelightTreeRepository(TerevoDatabase(driver))
    }

    @AfterTest
    fun close() {
        driver.close()
    }

    @Test
    fun `empty database loads an empty tree`() {
        val loaded = repository.load().shouldBeOk()

        assertEquals(0, loaded.size)
    }

    @Test
    fun `tree with people and relations survives a round trip`() {
        val bus = CommandBus(repository = repository)
        val father = richPerson()
        val mother = person(surname = "Петрова", givenName = "Анна", gender = Gender.FEMALE, born = 1905)
        val child = person(surname = "Иванов", givenName = "Пётр", gender = Gender.MALE, born = 1930)
        bus.execute(AddPerson(father)).shouldBeOk()
        bus.execute(AddPerson(mother)).shouldBeOk()
        bus.execute(AddPerson(child)).shouldBeOk()
        bus.execute(
            AddRelation(ParentChild.of(parent = father.id, child = child.id).shouldBeOk()),
        ).shouldBeOk()
        bus.execute(
            AddRelation(
                ParentChild.of(parent = mother.id, child = child.id, kind = ParentKind.ADOPTIVE).shouldBeOk(),
            ),
        ).shouldBeOk()
        bus.execute(
            AddRelation(
                Marriage.of(
                    first = father.id,
                    second = mother.id,
                    since = EventDate.Exact(LocalDate(1928, 4, 5)),
                    status = MarriageStatus.MARRIED,
                ).shouldBeOk(),
            ),
        ).shouldBeOk()

        val loaded = repository.load().shouldBeOk()

        assertEquals(bus.tree.value, loaded)
    }

    @Test
    fun `custom fields notes and places survive a round trip`() {
        val bus = CommandBus(repository = repository)
        val stored = richPerson()
        bus.execute(AddPerson(stored)).shouldBeOk()

        val loaded = repository.load().shouldBeOk().person(stored.id)

        assertEquals(stored, loaded)
        assertEquals("врач", loaded?.customFields?.get("Профессия"))
        assertEquals("Москва", loaded?.birthPlace?.title)
        assertEquals(Coordinates(55.75, 37.62), loaded?.birthPlace?.coordinates)
    }

    @Test
    fun `update replaces the stored person instead of duplicating it`() {
        val bus = CommandBus(repository = repository)
        val created = person(surname = "Иванов")
        bus.execute(AddPerson(created)).shouldBeOk()

        val renamed = created.with(name = name(surname = "Петров")).shouldBeOk()
        bus.execute(UpdatePerson(renamed)).shouldBeOk()

        val loaded = repository.load().shouldBeOk()
        assertEquals(1, loaded.size)
        assertEquals("Петров", loaded.person(created.id)?.name?.surname)
    }

    @Test
    fun `update drops custom fields that were removed`() {
        val bus = CommandBus(repository = repository)
        val created = richPerson()
        bus.execute(AddPerson(created)).shouldBeOk()

        val cleaned = created.with(customFields = emptyMap()).shouldBeOk()
        bus.execute(UpdatePerson(cleaned)).shouldBeOk()

        assertTrue(repository.load().shouldBeOk().person(created.id)?.customFields.orEmpty().isEmpty())
    }

    @Test
    fun `removing a person also removes their relations from storage`() {
        val bus = CommandBus(repository = repository)
        val parent = person()
        val child = person()
        bus.execute(AddPerson(parent)).shouldBeOk()
        bus.execute(AddPerson(child)).shouldBeOk()
        val link = ParentChild.of(parent = parent.id, child = child.id).shouldBeOk()
        bus.execute(AddRelation(link)).shouldBeOk()

        bus.execute(RemovePerson(parent.id)).shouldBeOk()

        val loaded = repository.load().shouldBeOk()
        assertEquals(1, loaded.size)
        assertNull(loaded.relation(link.id))
        assertEquals(bus.tree.value, loaded)
    }

    @Test
    fun `undo is persisted as well`() {
        val bus = CommandBus(repository = repository)
        val created = person()
        bus.execute(AddPerson(created)).shouldBeOk()

        bus.undo().shouldBeOk()

        assertEquals(0, repository.load().shouldBeOk().size)
    }

    @Test
    fun `approximate dates keep their precision in storage`() {
        val bus = CommandBus(repository = repository)
        val lifeSpan = LifeSpan.of(
            birth = EventDate.Approximate(LocalDate(1890, 1, 1), DatePrecision.DECADE),
            death = EventDate.Unknown,
        ).shouldBeOk()
        val stored = Person.create(name = name(), lifeSpan = lifeSpan).shouldBeOk()
        bus.execute(AddPerson(stored)).shouldBeOk()

        val loaded = repository.load().shouldBeOk().person(stored.id)

        assertEquals(EventDate.Approximate(LocalDate(1890, 1, 1), DatePrecision.DECADE), loaded?.lifeSpan?.birth)
    }

    @Test
    fun `event with several participants and roles survives a round trip`() {
        val bus = CommandBus(repository = repository)
        val groom = person(surname = "Иванов")
        val bride = person(surname = "Петрова", gender = Gender.FEMALE)
        val witness = person(surname = "Сидоров")
        bus.execute(AddPerson(groom)).shouldBeOk()
        bus.execute(AddPerson(bride)).shouldBeOk()
        bus.execute(AddPerson(witness)).shouldBeOk()
        val event = Event.of(
            type = "Свадьба",
            place = Place.of("Москва").shouldBeOk(),
            participants = listOf(
                EventParticipant(groom.id, "Жених"),
                EventParticipant(bride.id, "Невеста"),
                EventParticipant(witness.id, "Свидетель"),
            ),
            notes = "заметка",
        ).shouldBeOk()
        bus.execute(AddEvent(event)).shouldBeOk()

        val loaded = repository.load().shouldBeOk()

        assertEquals(bus.tree.value, loaded)
        assertEquals(event, loaded.event(event.id))
    }

    @Test
    fun `updating an event replaces participants instead of duplicating them`() {
        val bus = CommandBus(repository = repository)
        val honoree = person()
        bus.execute(AddPerson(honoree)).shouldBeOk()
        val event = Event.of(type = "Юбилей", participants = listOf(EventParticipant(honoree.id, "Именинник")))
            .shouldBeOk()
        bus.execute(AddEvent(event)).shouldBeOk()

        val renamed = event.with(type = "Выпускной").shouldBeOk()
        bus.execute(UpdateEvent(renamed)).shouldBeOk()

        val loaded = repository.load().shouldBeOk()
        assertEquals(renamed, loaded.event(event.id))
        assertEquals(1, loaded.event(event.id)?.participants?.size)
    }

    @Test
    fun `removing an event clears its participants from storage`() {
        val bus = CommandBus(repository = repository)
        val honoree = person()
        bus.execute(AddPerson(honoree)).shouldBeOk()
        val event = Event.of(type = "Юбилей", participants = listOf(EventParticipant(honoree.id, "Именинник")))
            .shouldBeOk()
        bus.execute(AddEvent(event)).shouldBeOk()

        bus.execute(RemoveEvent(event.id)).shouldBeOk()

        val loaded = repository.load().shouldBeOk()
        assertNull(loaded.event(event.id))
        assertEquals(1, loaded.size)
    }

    @Test
    fun `removing a person cascades to their events in storage`() {
        val bus = CommandBus(repository = repository)
        val honoree = person()
        bus.execute(AddPerson(honoree)).shouldBeOk()
        val event = Event.of(type = "Юбилей", participants = listOf(EventParticipant(honoree.id, "Именинник")))
            .shouldBeOk()
        bus.execute(AddEvent(event)).shouldBeOk()

        bus.execute(RemovePerson(honoree.id)).shouldBeOk()

        val loaded = repository.load().shouldBeOk()
        assertNull(loaded.event(event.id))
        assertEquals(bus.tree.value, loaded)
    }

    private fun richPerson(): Person = Person.create(
        name = name(surname = "Иванов", givenName = "Иван"),
        gender = Gender.MALE,
        lifeSpan = LifeSpan.of(
            birth = EventDate.Exact(LocalDate(1900, 5, 10)),
            death = EventDate.Approximate(LocalDate(1970, 1, 1), DatePrecision.YEAR),
        ).shouldBeOk(),
        birthPlace = Place.of("Москва", Coordinates(55.75, 37.62)).shouldBeOk(),
        deathPlace = Place.of("Тверь").shouldBeOk(),
        notes = "заметка",
        customFields = mapOf("Профессия" to "врач", "Награды" to "медаль"),
    ).shouldBeOk()
}
