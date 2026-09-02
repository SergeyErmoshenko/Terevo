package me.terevo.ui.person

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import me.terevo.domain.model.EventDate
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.Gender
import me.terevo.domain.model.LifeSpan
import me.terevo.domain.model.ParentChild
import me.terevo.domain.model.ParentKind
import me.terevo.domain.model.Person
import me.terevo.domain.model.PersonName
import me.terevo.domain.model.Place
import me.terevo.domain.model.RelationId
import me.terevo.testing.shouldBeOk

class PersonSearchTest {

    @Test
    fun `search covers name places notes and custom fields`() {
        val doctor = person("Иванов", Gender.MALE, 1980, "Казань", "кардиолог", mapOf("Награды" to "медаль"))
        val teacher = person("Петрова", Gender.FEMALE, 1990, "Москва", "учитель", emptyMap())
        val tree = FamilyTree.EMPTY.addPerson(doctor).shouldBeOk().addPerson(teacher).shouldBeOk()

        assertEquals(listOf(doctor.id), PersonSearch.find(tree, PersonSearchFilter(query = "иванов кардиолог")).map { it.id })
        assertEquals(listOf(doctor.id), PersonSearch.find(tree, PersonSearchFilter(query = "награды медаль")).map { it.id })
        assertEquals(listOf(teacher.id), PersonSearch.find(tree, PersonSearchFilter(place = "моск")).map { it.id })
    }

    @Test
    fun `filters combine gender birth years and parent presence`() {
        val parent = person("Иванов", Gender.MALE, 1960, "Казань", "", emptyMap())
        val child = person("Иванова", Gender.FEMALE, 1990, "Казань", "", emptyMap())
        val relation = ParentChild.of(RelationId.next(), parent.id, child.id, ParentKind.BIOLOGICAL).shouldBeOk()
        val tree = FamilyTree.EMPTY.addPerson(parent).shouldBeOk().addPerson(child).shouldBeOk().addRelation(relation).shouldBeOk()

        val result = PersonSearch.find(
            tree,
            PersonSearchFilter(gender = Gender.FEMALE, birthYearFrom = 1985, birthYearTo = 1995, hasParents = true),
        )

        assertEquals(listOf(child.id), result.map { it.id })
    }

    @Test
    fun `filter finds people without dates`() {
        val dated = person("Иванов", Gender.MALE, 1960, "Казань", "", emptyMap())
        val undated = Person.create(name = PersonName.of("Петров", "Пётр").shouldBeOk()).shouldBeOk()
        val tree = FamilyTree.EMPTY.addPerson(dated).shouldBeOk().addPerson(undated).shouldBeOk()

        val result = PersonSearch.find(tree, PersonSearchFilter(hasDates = false))

        assertEquals(listOf(undated.id), result.map { it.id })
    }

    private fun person(
        surname: String,
        gender: Gender,
        year: Int,
        place: String,
        notes: String,
        customFields: Map<String, String>,
    ): Person = Person.create(
        name = PersonName.of(surname, "Имя").shouldBeOk(),
        gender = gender,
        lifeSpan = LifeSpan.of(EventDate.Exact(LocalDate(year, 1, 1)), EventDate.Unknown).shouldBeOk(),
        birthPlace = Place.of(place).shouldBeOk(),
        notes = notes,
        customFields = customFields,
    ).shouldBeOk()
}
