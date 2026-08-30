package me.terevo.domain.model

import me.terevo.domain.DomainError
import me.terevo.domain.Outcome

data class Coordinates(val latitude: Double, val longitude: Double)

class Place private constructor(
    val title: String,
    val coordinates: Coordinates?,
) {
    override fun equals(other: Any?): Boolean =
        other is Place && title == other.title && coordinates == other.coordinates

    override fun hashCode(): Int = title.hashCode() * 31 + coordinates.hashCode()

    override fun toString(): String = title

    companion object {
        fun of(title: String, coordinates: Coordinates? = null): Outcome<Place> {
            val normalized = title.trim().replace(WHITESPACE, " ")
            return if (normalized.isEmpty()) {
                Outcome.Err(DomainError.Place.Blank)
            } else {
                Outcome.Ok(Place(normalized, coordinates))
            }
        }

        private val WHITESPACE = Regex("\\s+")
    }
}
