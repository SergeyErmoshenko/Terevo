package me.terevo.persistence

import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.invariant.Change
import me.terevo.domain.model.*
import me.terevo.domain.port.TreeRepository
import me.terevo.persistence.db.TerevoDatabase
import java.sql.SQLException

class SqlDelightTreeRepository(private val database: TerevoDatabase) : TreeRepository {

    override fun load(): Outcome<FamilyTree> = guarded {
        val customFields = database.personCustomFieldQueries.selectAll().executeAsList()
            .groupBy { it.person_id }
            .mapValues { entry -> entry.value.associate { it.field_key to it.field_value } }
        val attachments = database.mediaQueries.selectAllAttachments().executeAsList()
            .groupBy { it.person_id }
            .mapValues { entry -> entry.value.mapNotNull { parseId(it.media_id) }.map { MediaId(it) } }

        val persons = mutableListOf<Person>()
        for (row in database.personQueries.selectAll().executeAsList()) {
            val mapped = PersonMapper.toDomain(
                row = row,
                customFields = customFields[row.id].orEmpty(),
                mediaIds = attachments[row.id].orEmpty(),
            )
            when (mapped) {
                is Outcome.Ok -> persons.add(mapped.value)
                is Outcome.Err -> return@guarded mapped
            }
        }

        val relations = mutableListOf<Relation>()
        for (row in database.parentChildQueries.selectAll().executeAsList()) {
            when (val mapped = RelationMapper.toDomain(row)) {
                is Outcome.Ok -> relations.add(mapped.value)
                is Outcome.Err -> return@guarded mapped
            }
        }
        for (row in database.marriageQueries.selectAll().executeAsList()) {
            when (val mapped = RelationMapper.toDomain(row)) {
                is Outcome.Ok -> relations.add(mapped.value)
                is Outcome.Err -> return@guarded mapped
            }
        }

        FamilyTree.of(persons, relations)
    }

    override fun apply(changes: List<Change>): Outcome<Unit> = guarded {
        database.transaction {
            for (change in changes) {
                when (change) {
                    is Change.AddPerson -> insertPerson(change.person)
                    is Change.UpdatePerson -> updatePerson(change.person)
                    is Change.RemovePerson -> database.personQueries.delete(change.id.toString())
                    is Change.AddRelation -> writeRelation(change.relation)
                    is Change.RemoveRelation -> deleteRelation(change.id.toString())
                }
            }
        }
        Outcome.Ok(Unit)
    }

    private fun insertPerson(person: Person) {
        val columns = PersonMapper.toColumns(person)
        database.personQueries.insert(
            id = columns.id,
            surname = columns.surname,
            given_name = columns.givenName,
            patronymic = columns.patronymic,
            maiden_name = columns.maidenName,
            gender = columns.gender,
            birth_kind = columns.birth.kind,
            birth_value = columns.birth.value,
            birth_end = columns.birth.end,
            birth_precision = columns.birth.precision,
            death_kind = columns.death.kind,
            death_value = columns.death.value,
            death_end = columns.death.end,
            death_precision = columns.death.precision,
            birth_place = columns.birthPlace,
            birth_latitude = columns.birthLatitude,
            birth_longitude = columns.birthLongitude,
            death_place = columns.deathPlace,
            death_latitude = columns.deathLatitude,
            death_longitude = columns.deathLongitude,
            residence = columns.residence,
            residence_latitude = columns.residenceLatitude,
            residence_longitude = columns.residenceLongitude,
            occupation = columns.occupation,
            notes = columns.notes,
        )
        writeSatellites(person, columns.id)
    }

    private fun updatePerson(person: Person) {
        val columns = PersonMapper.toColumns(person)
        database.personQueries.update(
            surname = columns.surname,
            given_name = columns.givenName,
            patronymic = columns.patronymic,
            maiden_name = columns.maidenName,
            gender = columns.gender,
            birth_kind = columns.birth.kind,
            birth_value = columns.birth.value,
            birth_end = columns.birth.end,
            birth_precision = columns.birth.precision,
            death_kind = columns.death.kind,
            death_value = columns.death.value,
            death_end = columns.death.end,
            death_precision = columns.death.precision,
            birth_place = columns.birthPlace,
            birth_latitude = columns.birthLatitude,
            birth_longitude = columns.birthLongitude,
            death_place = columns.deathPlace,
            death_latitude = columns.deathLatitude,
            death_longitude = columns.deathLongitude,
            residence = columns.residence,
            residence_latitude = columns.residenceLatitude,
            residence_longitude = columns.residenceLongitude,
            occupation = columns.occupation,
            notes = columns.notes,
            id = columns.id,
        )
        writeSatellites(person, columns.id)
    }

    private fun writeSatellites(person: Person, id: String) {
        database.personCustomFieldQueries.deleteByPerson(id)
        for ((key, value) in person.customFields) {
            database.personCustomFieldQueries.insert(
                person_id = id,
                field_key = key,
                field_value = value,
            )
        }
        database.mediaQueries.deleteAttachmentsByPerson(id)
        person.mediaIds.forEachIndexed { position, mediaId ->
            database.mediaQueries.insertAttachment(
                person_id = id,
                media_id = mediaId.toString(),
                position = position.toLong(),
            )
        }
    }

    private fun writeRelation(relation: Relation) {
        when (relation) {
            is ParentChild -> {
                val columns = RelationMapper.toColumns(relation)
                database.parentChildQueries.insert(
                    id = columns.id,
                    parent_id = columns.parentId,
                    child_id = columns.childId,
                    kind = columns.kind,
                )
            }

            is Marriage -> {
                val columns = RelationMapper.toColumns(relation)
                database.marriageQueries.insert(
                    id = columns.id,
                    spouse_a = columns.spouseA,
                    spouse_b = columns.spouseB,
                    since_kind = columns.since.kind,
                    since_value = columns.since.value,
                    since_end = columns.since.end,
                    since_precision = columns.since.precision,
                    until_kind = columns.until.kind,
                    until_value = columns.until.value,
                    until_end = columns.until.end,
                    until_precision = columns.until.precision,
                    status = columns.status,
                    place = columns.place,
                    place_latitude = columns.placeLatitude,
                    place_longitude = columns.placeLongitude,
                )
            }
        }
    }

    private fun deleteRelation(id: String) {
        database.parentChildQueries.delete(id)
        database.marriageQueries.delete(id)
    }

    private fun <T> guarded(block: () -> Outcome<T>): Outcome<T> = try {
        block()
    } catch (failure: SQLException) {
        Outcome.Err(DomainError.Storage.Failure(failure.message.orEmpty()))
    } catch (failure: IllegalStateException) {
        Outcome.Err(DomainError.Storage.Failure(failure.message.orEmpty()))
    }
}
