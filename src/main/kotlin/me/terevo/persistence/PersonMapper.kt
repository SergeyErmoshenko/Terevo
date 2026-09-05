package me.terevo.persistence

import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.getOrNull
import me.terevo.domain.model.*
import java.util.*
import me.terevo.persistence.db.Person as PersonRow

data class PersonColumns(
    val id: String,
    val surname: String,
    val givenName: String,
    val patronymic: String,
    val maidenName: String,
    val gender: String,
    val birth: EncodedDate,
    val death: EncodedDate,
    val birthPlace: String?,
    val birthLatitude: Double?,
    val birthLongitude: Double?,
    val deathPlace: String?,
    val deathLatitude: Double?,
    val deathLongitude: Double?,
    val residence: String?,
    val residenceLatitude: Double?,
    val residenceLongitude: Double?,
    val occupation: String,
    val notes: String,
)

object PersonMapper {

    fun toColumns(person: Person): PersonColumns = PersonColumns(
        id = person.id.toString(),
        surname = person.name.surname,
        givenName = person.name.givenName,
        patronymic = person.name.patronymic,
        maidenName = person.name.maidenName,
        gender = person.gender.name,
        birth = EventDateCodec.encode(person.lifeSpan.birth),
        death = EventDateCodec.encode(person.lifeSpan.death),
        birthPlace = person.birthPlace?.title,
        birthLatitude = person.birthPlace?.coordinates?.latitude,
        birthLongitude = person.birthPlace?.coordinates?.longitude,
        deathPlace = person.deathPlace?.title,
        deathLatitude = person.deathPlace?.coordinates?.latitude,
        deathLongitude = person.deathPlace?.coordinates?.longitude,
        residence = person.residence?.title,
        residenceLatitude = person.residence?.coordinates?.latitude,
        residenceLongitude = person.residence?.coordinates?.longitude,
        occupation = person.occupation,
        notes = person.notes,
    )

    fun toDomain(
        row: PersonRow,
        customFields: Map<String, String>,
        mediaIds: List<MediaId>,
    ): Outcome<Person> {
        val id = parseId(row.id) ?: return corrupted(row.id, "identifier is not a UUID")
        val name = PersonName.of(row.surname, row.given_name, row.patronymic, row.maiden_name).getOrNull()
            ?: return corrupted(row.id, "name is empty")
        val gender = Gender.entries.firstOrNull { it.name == row.gender }
            ?: return corrupted(row.id, "unknown gender ${row.gender}")
        val birth = EventDateCodec.decode(
            EncodedDate(row.birth_kind, row.birth_value, row.birth_end, row.birth_precision),
        ) ?: return corrupted(row.id, "birth date is unreadable")
        val death = EventDateCodec.decode(
            EncodedDate(row.death_kind, row.death_value, row.death_end, row.death_precision),
        ) ?: return corrupted(row.id, "death date is unreadable")
        val lifeSpan = LifeSpan.of(birth, death).getOrNull()
            ?: return corrupted(row.id, "death precedes birth")

        return Person.create(
            id = PersonId(id),
            name = name,
            gender = gender,
            lifeSpan = lifeSpan,
            birthPlace = place(row.birth_place, row.birth_latitude, row.birth_longitude),
            deathPlace = place(row.death_place, row.death_latitude, row.death_longitude),
            residence = place(row.residence, row.residence_latitude, row.residence_longitude),
            occupation = row.occupation,
            notes = row.notes,
            customFields = customFields,
            mediaIds = mediaIds,
        )
    }

    private fun place(title: String?, latitude: Double?, longitude: Double?): Place? {
        if (title == null) return null
        val coordinates = if (latitude != null && longitude != null) Coordinates(latitude, longitude) else null
        return Place.of(title, coordinates).getOrNull()
    }

    private fun corrupted(id: String, reason: String): Outcome<Nothing> =
        Outcome.Err(DomainError.Storage.CorruptedRecord("person", id, reason))
}

fun parseId(text: String): UUID? = try {
    UUID.fromString(text)
} catch (invalid: IllegalArgumentException) {
    null
}
