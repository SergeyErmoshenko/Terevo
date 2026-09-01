package me.terevo.domain.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import me.terevo.domain.DomainError
import me.terevo.domain.Outcome

class Person private constructor(
    val id: PersonId,
    val name: PersonName,
    val gender: Gender,
    val lifeSpan: LifeSpan,
    val birthPlace: Place?,
    val deathPlace: Place?,
    val notes: String,
    val customFields: PersistentMap<String, String>,
    val mediaIds: PersistentList<MediaId>,
) {
    val isAlive: Boolean get() = lifeSpan.isAlive

    @Suppress("LongParameterList")
    fun with(
        name: PersonName = this.name,
        gender: Gender = this.gender,
        lifeSpan: LifeSpan = this.lifeSpan,
        birthPlace: Place? = this.birthPlace,
        deathPlace: Place? = this.deathPlace,
        notes: String = this.notes,
        customFields: Map<String, String> = this.customFields,
        mediaIds: List<MediaId> = this.mediaIds,
    ): Outcome<Person> = create(
        id = id,
        name = name,
        gender = gender,
        lifeSpan = lifeSpan,
        birthPlace = birthPlace,
        deathPlace = deathPlace,
        notes = notes,
        customFields = customFields,
        mediaIds = mediaIds,
    )

    override fun equals(other: Any?): Boolean =
        other is Person &&
            id == other.id &&
            name == other.name &&
            gender == other.gender &&
            lifeSpan == other.lifeSpan &&
            birthPlace == other.birthPlace &&
            deathPlace == other.deathPlace &&
            notes == other.notes &&
            customFields == other.customFields &&
            mediaIds == other.mediaIds

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = result * PRIME + name.hashCode()
        result = result * PRIME + gender.hashCode()
        result = result * PRIME + lifeSpan.hashCode()
        result = result * PRIME + birthPlace.hashCode()
        result = result * PRIME + deathPlace.hashCode()
        result = result * PRIME + notes.hashCode()
        result = result * PRIME + customFields.hashCode()
        result = result * PRIME + mediaIds.hashCode()
        return result
    }

    override fun toString(): String = "Person($id, ${name.display})"

    companion object {
        private const val PRIME = 31

        @Suppress("LongParameterList")
        fun create(
            id: PersonId = PersonId.next(),
            name: PersonName,
            gender: Gender = Gender.UNKNOWN,
            lifeSpan: LifeSpan = LifeSpan.UNKNOWN,
            birthPlace: Place? = null,
            deathPlace: Place? = null,
            notes: String = "",
            customFields: Map<String, String> = emptyMap(),
            mediaIds: List<MediaId> = emptyList(),
        ): Outcome<Person> {
            if (gender != Gender.FEMALE && name.maidenName.isNotEmpty()) {
                return Outcome.Err(DomainError.Name.MaidenNameRequiresFemaleGender)
            }
            val blankKey = customFields.keys.firstOrNull { it.isBlank() }
            if (blankKey != null) return Outcome.Err(DomainError.CustomField.BlankKey)
            val duplicateMedia = mediaIds.groupingBy { it }.eachCount().any { it.value > 1 }
            if (duplicateMedia) return Outcome.Err(DomainError.Media.Duplicate)
            return Outcome.Ok(
                Person(
                    id = id,
                    name = name,
                    gender = gender,
                    lifeSpan = lifeSpan,
                    birthPlace = birthPlace,
                    deathPlace = deathPlace,
                    notes = notes.trim(),
                    customFields = customFields.mapKeys { it.key.trim() }.toPersistentMap(),
                    mediaIds = mediaIds.toPersistentList(),
                ),
            )
        }

        val NO_CUSTOM_FIELDS: PersistentMap<String, String> = persistentMapOf()

        val NO_MEDIA: PersistentList<MediaId> = persistentListOf()
    }
}
