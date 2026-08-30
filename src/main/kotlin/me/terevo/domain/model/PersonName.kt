package me.terevo.domain.model

import me.terevo.domain.DomainError
import me.terevo.domain.Outcome

class PersonName private constructor(
    val surname: String,
    val givenName: String,
    val patronymic: String,
    val maidenName: String,
) {
    val display: String
        get() = listOf(surname, givenName, patronymic)
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    val sortKey: String
        get() = listOf(surname, givenName, patronymic).joinToString(" ").lowercase()

    fun with(
        surname: String = this.surname,
        givenName: String = this.givenName,
        patronymic: String = this.patronymic,
        maidenName: String = this.maidenName,
    ): Outcome<PersonName> = of(surname, givenName, patronymic, maidenName)

    override fun equals(other: Any?): Boolean =
        other is PersonName &&
            surname == other.surname &&
            givenName == other.givenName &&
            patronymic == other.patronymic &&
            maidenName == other.maidenName

    override fun hashCode(): Int =
        listOf(surname, givenName, patronymic, maidenName).fold(0) { acc, part -> acc * 31 + part.hashCode() }

    override fun toString(): String = display

    companion object {
        fun of(
            surname: String,
            givenName: String,
            patronymic: String = "",
            maidenName: String = "",
        ): Outcome<PersonName> {
            val name = PersonName(
                surname = normalize(surname),
                givenName = normalize(givenName),
                patronymic = normalize(patronymic),
                maidenName = normalize(maidenName),
            )
            return if (name.surname.isEmpty() && name.givenName.isEmpty()) {
                Outcome.Err(DomainError.Name.Blank)
            } else {
                Outcome.Ok(name)
            }
        }

        private fun normalize(value: String): String = value.trim().replace(WHITESPACE, " ")

        private val WHITESPACE = Regex("\\s+")
    }
}
