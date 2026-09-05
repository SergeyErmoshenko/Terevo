package me.terevo.domain.port

import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.model.PersonId

interface NodePositionRepository {
    fun loadAll(): Outcome<Map<PersonId, Pair<Double, Double>>>

    fun set(id: PersonId, x: Double, y: Double): Outcome<Unit>

    fun clear(id: PersonId): Outcome<Unit>

    fun clearAll(): Outcome<Unit>

    companion object {
        val NONE: NodePositionRepository = object : NodePositionRepository {
            private val error = Outcome.Err(DomainError.Storage.Failure("Позиции недоступны"))

            override fun loadAll(): Outcome<Map<PersonId, Pair<Double, Double>>> = error
            override fun set(id: PersonId, x: Double, y: Double): Outcome<Unit> = error
            override fun clear(id: PersonId): Outcome<Unit> = error
            override fun clearAll(): Outcome<Unit> = error
        }
    }
}
