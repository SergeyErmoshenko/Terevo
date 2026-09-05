package me.terevo.persistence

import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.getOrNull
import me.terevo.domain.model.*
import me.terevo.persistence.db.Marriage as MarriageRow
import me.terevo.persistence.db.Parent_child as ParentChildRow

data class ParentChildColumns(
    val id: String,
    val parentId: String,
    val childId: String,
    val kind: String,
)

data class MarriageColumns(
    val id: String,
    val spouseA: String,
    val spouseB: String,
    val since: EncodedDate,
    val until: EncodedDate,
    val status: String,
    val place: String?,
    val placeLatitude: Double?,
    val placeLongitude: Double?,
)

object RelationMapper {

    fun toColumns(relation: ParentChild): ParentChildColumns = ParentChildColumns(
        id = relation.id.toString(),
        parentId = relation.parent.toString(),
        childId = relation.child.toString(),
        kind = relation.kind.name,
    )

    fun toColumns(relation: Marriage): MarriageColumns = MarriageColumns(
        id = relation.id.toString(),
        spouseA = relation.spouseA.toString(),
        spouseB = relation.spouseB.toString(),
        since = EventDateCodec.encode(relation.since),
        until = EventDateCodec.encode(relation.until),
        status = relation.status.name,
        place = relation.place?.title,
        placeLatitude = relation.place?.coordinates?.latitude,
        placeLongitude = relation.place?.coordinates?.longitude,
    )

    fun toDomain(row: ParentChildRow): Outcome<ParentChild> {
        val id = parseId(row.id) ?: return corrupted("parent_child", row.id, "identifier is not a UUID")
        val parent = parseId(row.parent_id) ?: return corrupted("parent_child", row.id, "parent is not a UUID")
        val child = parseId(row.child_id) ?: return corrupted("parent_child", row.id, "child is not a UUID")
        val kind = ParentKind.entries.firstOrNull { it.name == row.kind }
            ?: return corrupted("parent_child", row.id, "unknown kind ${row.kind}")
        return ParentChild.of(RelationId(id), PersonId(parent), PersonId(child), kind)
    }

    fun toDomain(row: MarriageRow): Outcome<Marriage> {
        val id = parseId(row.id) ?: return corrupted("marriage", row.id, "identifier is not a UUID")
        val first = parseId(row.spouse_a) ?: return corrupted("marriage", row.id, "spouse is not a UUID")
        val second = parseId(row.spouse_b) ?: return corrupted("marriage", row.id, "spouse is not a UUID")
        val since = EventDateCodec.decode(
            EncodedDate(row.since_kind, row.since_value, row.since_end, row.since_precision),
        ) ?: return corrupted("marriage", row.id, "start date is unreadable")
        val until = EventDateCodec.decode(
            EncodedDate(row.until_kind, row.until_value, row.until_end, row.until_precision),
        ) ?: return corrupted("marriage", row.id, "end date is unreadable")
        val status = MarriageStatus.entries.firstOrNull { it.name == row.status }
            ?: return corrupted("marriage", row.id, "unknown status ${row.status}")
        val place = place(row.place, row.place_latitude, row.place_longitude)
        return Marriage.of(RelationId(id), PersonId(first), PersonId(second), since, until, status, place)
    }

    private fun place(title: String?, latitude: Double?, longitude: Double?): Place? {
        if (title == null) return null
        val coordinates = if (latitude != null && longitude != null) Coordinates(latitude, longitude) else null
        return Place.of(title, coordinates).getOrNull()
    }

    private fun corrupted(table: String, id: String, reason: String): Outcome<Nothing> =
        Outcome.Err(DomainError.Storage.CorruptedRecord(table, id, reason))
}
