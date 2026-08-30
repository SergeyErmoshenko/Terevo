package me.terevo.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearsUntil
import me.terevo.domain.DomainError
import me.terevo.domain.Outcome

class LifeSpan private constructor(
    val birth: EventDate,
    val death: EventDate,
) {
    val isAlive: Boolean get() = !death.isKnown

    val hasBothDates: Boolean get() = birth.isKnown && death.isKnown

    fun ageAt(date: LocalDate): Int? {
        val born = birth.interval?.from ?: return null
        if (date < born) return null
        return born.yearsUntil(date)
    }

    val ageAtDeath: Int?
        get() {
            val died = death.interval?.from ?: return null
            return ageAt(died)
        }

    fun with(birth: EventDate = this.birth, death: EventDate = this.death): Outcome<LifeSpan> = of(birth, death)

    override fun equals(other: Any?): Boolean = other is LifeSpan && birth == other.birth && death == other.death

    override fun hashCode(): Int = birth.hashCode() * 31 + death.hashCode()

    override fun toString(): String = "LifeSpan($birth..$death)"

    companion object {
        val UNKNOWN: LifeSpan = LifeSpan(EventDate.Unknown, EventDate.Unknown)

        fun of(birth: EventDate, death: EventDate): Outcome<LifeSpan> =
            if (death.definitelyBefore(birth)) {
                Outcome.Err(DomainError.Date.DeathBeforeBirth(birth, death))
            } else {
                Outcome.Ok(LifeSpan(birth, death))
            }
    }
}
