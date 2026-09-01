package me.terevo.ui.person

import kotlinx.datetime.LocalDate
import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.flatMap
import me.terevo.domain.model.DatePrecision
import me.terevo.domain.model.EventDate
import me.terevo.domain.model.Gender
import me.terevo.domain.model.LifeSpan
import me.terevo.domain.model.Person
import me.terevo.domain.model.PersonId
import me.terevo.domain.model.PersonName
import me.terevo.domain.model.Place

enum class EventDateMode {
    EXACT,
    APPROXIMATE,
    RANGE,
    UNKNOWN,
}

data class EventDateInput(
    val mode: EventDateMode = EventDateMode.UNKNOWN,
    val value: String = "",
    val end: String = "",
    val precision: DatePrecision = DatePrecision.YEAR,
)

data class PersonFormState(
    val id: PersonId = PersonId.next(),
    val surname: String = "",
    val givenName: String = "",
    val patronymic: String = "",
    val maidenName: String = "",
    val gender: Gender = Gender.UNKNOWN,
    val birth: EventDateInput = EventDateInput(),
    val isAlive: Boolean = true,
    val death: EventDateInput = EventDateInput(),
    val birthPlace: String = "",
    val deathPlace: String = "",
    val notes: String = "",
    val original: Person? = null,
    val blockingError: String? = null,
    val warnings: List<String> = emptyList(),
    val isDiscardConfirmationVisible: Boolean = false,
) {
    val isDirty: Boolean get() = original?.let { toComparable() != fromPerson(it).toComparable() } ?: hasInput
    val canSave: Boolean get() = blockingError == null && (surname.isNotBlank() || givenName.isNotBlank())
    private val hasInput: Boolean get() = surname.isNotBlank() || givenName.isNotBlank() || notes.isNotBlank()

    private fun toComparable(): List<Any> = listOf(
        surname, givenName, patronymic, maidenName, gender, birth, isAlive, death, birthPlace, deathPlace, notes,
    )

    companion object {
        fun fromPerson(person: Person): PersonFormState = PersonFormState(
            id = person.id,
            surname = person.name.surname,
            givenName = person.name.givenName,
            patronymic = person.name.patronymic,
            maidenName = person.name.maidenName,
            gender = person.gender,
            birth = person.lifeSpan.birth.toInput(),
            isAlive = person.isAlive,
            death = person.lifeSpan.death.toInput(),
            birthPlace = person.birthPlace?.title.orEmpty(),
            deathPlace = person.deathPlace?.title.orEmpty(),
            notes = person.notes,
            original = person,
        )
    }
}

fun PersonFormState.toPerson(): Outcome<Person> = PersonName.of(
    surname,
    givenName,
    patronymic,
    maidenName.takeIf { gender == Gender.FEMALE }.orEmpty(),
).flatMap { name ->
    birth.toEventDate().flatMap { birthDate ->
        val deathDate = if (isAlive) Outcome.Ok(EventDate.Unknown) else death.toEventDate()
        deathDate.flatMap { diedOn ->
            LifeSpan.of(birthDate, diedOn).flatMap { lifeSpan ->
                placeOf(birthPlace).flatMap { bornAt ->
                    val diedAtResult = if (isAlive) Outcome.Ok(null) else placeOf(deathPlace)
                    diedAtResult.flatMap { diedAt ->
                        Person.create(
                            id = id,
                            name = name,
                            gender = gender,
                            lifeSpan = lifeSpan,
                            birthPlace = bornAt,
                            deathPlace = diedAt,
                            notes = notes,
                        )
                    }
                }
            }
        }
    }
}

fun validatePersonForm(state: PersonFormState): PersonFormState = when (val person = state.toPerson()) {
    is Outcome.Ok -> state.copy(blockingError = null)
    is Outcome.Err -> state.copy(blockingError = person.error.toRussianMessage())
}

fun DomainError.toRussianMessage(): String = when (this) {
    DomainError.Name.Blank -> "Укажите фамилию или имя"
    DomainError.Name.MaidenNameRequiresFemaleGender -> "Девичью фамилию можно указать только для женщины"
    is DomainError.Date.InvalidFormat -> "Введите дату в формате ДД.ММ.ГГГГ"
    is DomainError.Date.RangeReversed -> "Начало диапазона даты должно быть раньше окончания"
    is DomainError.Date.DeathBeforeBirth -> "Дата смерти не может быть раньше даты рождения"
    DomainError.Place.Blank -> "Название места не может быть пустым"
    else -> "Не удалось сохранить изменения"
}

private fun EventDateInput.toEventDate(): Outcome<EventDate> = when (mode) {
    EventDateMode.EXACT -> parseDate(value).mapDate(EventDate::Exact)
    EventDateMode.APPROXIMATE -> parseDate(value).mapDate { EventDate.Approximate(it, precision) }
    EventDateMode.RANGE -> parseDate(value).flatMap { from -> parseDate(end).flatMap { to -> EventDate.Range.of(from, to) } }
    EventDateMode.UNKNOWN -> Outcome.Ok(EventDate.Unknown)
}

private fun parseDate(value: String): Outcome<LocalDate> = try {
    Outcome.Ok(parseDisplayDate(value))
} catch (_: IllegalArgumentException) {
    Outcome.Err(DomainError.Date.InvalidFormat(value))
}

private fun placeOf(value: String): Outcome<Place?> =
    if (value.isBlank()) Outcome.Ok(null) else when (val place = Place.of(value)) {
        is Outcome.Ok -> Outcome.Ok(place.value)
        is Outcome.Err -> place
    }

private fun EventDate.toInput(): EventDateInput = when (this) {
    is EventDate.Exact -> EventDateInput(EventDateMode.EXACT, date.toDisplayDate())
    is EventDate.Approximate -> EventDateInput(EventDateMode.APPROXIMATE, around.toDisplayDate(), precision = precision)
    is EventDate.Range -> EventDateInput(EventDateMode.RANGE, from.toDisplayDate(), to.toDisplayDate())
    EventDate.Unknown -> EventDateInput()
}

private inline fun <T> Outcome<LocalDate>.mapDate(transform: (LocalDate) -> T): Outcome<T> = when (this) {
    is Outcome.Ok -> Outcome.Ok(transform(value))
    is Outcome.Err -> this
}
