package me.terevo.persistence

import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.model.PersonId
import me.terevo.domain.port.NodePositionRepository
import me.terevo.persistence.db.TerevoDatabase
import java.sql.SQLException

class SqlNodePositionRepository(
    private val database: TerevoDatabase,
) : NodePositionRepository {

    override fun loadAll(): Outcome<Map<PersonId, Pair<Double, Double>>> = guarded {
        val positions = database.nodePositionQueries.selectAllPositions().executeAsList()
            .associate { row -> PersonId.parse(row.person_id) to (row.x to row.y) }
        Outcome.Ok(positions)
    }

    override fun set(id: PersonId, x: Double, y: Double): Outcome<Unit> = guarded {
        database.transaction {
            val existing = database.nodePositionQueries.selectPositionById(id.toString()).executeAsOneOrNull()
            if (existing != null) {
                database.nodePositionQueries.updatePosition(x = x, y = y, person_id = id.toString())
            } else {
                database.nodePositionQueries.insertPosition(person_id = id.toString(), x = x, y = y)
            }
        }
        Outcome.Ok(Unit)
    }

    override fun clear(id: PersonId): Outcome<Unit> = guarded {
        database.nodePositionQueries.deletePosition(id.toString())
        Outcome.Ok(Unit)
    }

    override fun clearAll(): Outcome<Unit> = guarded {
        database.nodePositionQueries.deleteAllPositions()
        Outcome.Ok(Unit)
    }

    private inline fun <T> guarded(block: () -> Outcome<T>): Outcome<T> = try {
        block()
    } catch (failure: SQLException) {
        Outcome.Err(DomainError.Storage.Failure(failure.message.orEmpty()))
    }
}
