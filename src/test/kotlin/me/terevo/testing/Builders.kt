package me.terevo.testing

import kotlinx.datetime.LocalDate
import me.terevo.domain.model.EventDate
import me.terevo.domain.model.Gender
import me.terevo.domain.model.LifeSpan
import me.terevo.domain.model.Person
import me.terevo.domain.model.PersonId
import me.terevo.domain.model.PersonName

fun year(value: Int): EventDate = EventDate.Exact(LocalDate(value, 1, 1))

fun name(surname: String = "Иванов", givenName: String = "Иван"): PersonName =
    PersonName.of(surname, givenName).shouldBeOk()

fun lifeSpan(born: Int? = null, died: Int? = null): LifeSpan =
    LifeSpan.of(
        birth = born?.let { year(it) } ?: EventDate.Unknown,
        death = died?.let { year(it) } ?: EventDate.Unknown,
    ).shouldBeOk()

fun person(
    id: PersonId = PersonId.next(),
    surname: String = "Иванов",
    givenName: String = "Иван",
    gender: Gender = Gender.UNKNOWN,
    born: Int? = null,
    died: Int? = null,
): Person = Person.create(
    id = id,
    name = name(surname, givenName),
    gender = gender,
    lifeSpan = lifeSpan(born, died),
).shouldBeOk()
