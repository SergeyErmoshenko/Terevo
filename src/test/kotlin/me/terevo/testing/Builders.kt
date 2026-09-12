package me.terevo.testing

import kotlinx.datetime.LocalDate
import me.terevo.domain.model.*

fun year(value: Int): EventDate = EventDate.Exact(LocalDate(value, 1, 1))

fun name(surname: String = "Иванов", givenName: String = "Иван"): PersonName =
    PersonName.of(surname, givenName).shouldBeOk()

fun place(title: String): Place = Place.of(title).shouldBeOk()

fun lifeSpan(born: Int? = null, died: Int? = null): LifeSpan =
    LifeSpan.of(
        birth = born?.let { year(it) } ?: EventDate.Unknown,
        death = died?.let { year(it) } ?: EventDate.Unknown,
    ).shouldBeOk()

@Suppress("LongParameterList")
fun person(
    id: PersonId = PersonId.next(),
    surname: String = "Иванов",
    givenName: String = "Иван",
    gender: Gender = Gender.UNKNOWN,
    born: Int? = null,
    died: Int? = null,
    birthPlace: String? = null,
    deathPlace: String? = null,
): Person = Person.create(
    id = id,
    name = name(surname, givenName),
    gender = gender,
    lifeSpan = lifeSpan(born, died),
    birthPlace = birthPlace?.let(::place),
    deathPlace = deathPlace?.let(::place),
).shouldBeOk()
