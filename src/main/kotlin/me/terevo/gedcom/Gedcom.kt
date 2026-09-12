package me.terevo.gedcom

import kotlinx.datetime.LocalDate
import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.command.AddPerson
import me.terevo.domain.command.AddRelation
import me.terevo.domain.command.Batch
import me.terevo.domain.model.*

data class GedcomLine(
    val level: Int,
    val pointer: String?,
    val tag: String,
    val value: String,
)

data class GedcomPreview(
    val people: Int,
    val families: Int,
    val skippedTags: Set<String>,
    val command: Batch,
)

object GedcomCodec {
    fun parse(text: String): Outcome<GedcomPreview> = try {
        val lines = text.lineSequence().filter(String::isNotBlank).map(::parseLine).toList()
        val records = records(lines)
        val people = linkedMapOf<String, Person>()
        val skipped = mutableSetOf<String>()
        records.filter { it.first().tag == "INDI" }.forEach { record ->
            val pointer = record.first().pointer ?: return@forEach
            val nameValue = record.firstOrNull { it.level == 1 && it.tag == "NAME" }?.value.orEmpty()
            val (given, surname) = parseName(nameValue)
            val safeGiven = given.ifBlank { "Без имени" }
            val sex = when (record.firstOrNull { it.level == 1 && it.tag == "SEX" }?.value) {
                "M" -> Gender.MALE
                "F" -> Gender.FEMALE
                else -> Gender.UNKNOWN
            }
            val birth = event(record, "BIRT")
            val death = event(record, "DEAT")
            val notes = record.filter { it.level == 1 && it.tag == "NOTE" }.joinToString("\n") { it.value }
            val known = setOf("INDI", "NAME", "SEX", "BIRT", "DEAT", "DATE", "PLAC", "NOTE", "FAMC", "FAMS")
            skipped += record.map { it.tag }.filterNot(known::contains)
            val person = Person.create(
                name = PersonName.of(surname, safeGiven).ok(),
                gender = sex,
                lifeSpan = LifeSpan.of(birth.first, death.first).ok(),
                birthPlace = birth.second?.let { me.terevo.domain.model.Place.of(it).ok() },
                deathPlace = death.second?.let { me.terevo.domain.model.Place.of(it).ok() },
                notes = notes,
            ).ok()
            people[pointer] = person
        }
        val commands = people.values.map(::AddPerson).toMutableList<me.terevo.domain.command.Command>()
        val families = records.filter { it.first().tag == "FAM" }
        families.forEach { record ->
            val spouses = listOfNotNull(
                record.firstOrNull { it.level == 1 && it.tag == "HUSB" }?.value?.let(people::get),
                record.firstOrNull { it.level == 1 && it.tag == "WIFE" }?.value?.let(people::get),
            )
            if (spouses.size == 2) {
                commands += AddRelation(
                    Marriage.of(
                        id = RelationId.next(),
                        first = spouses[0].id,
                        second = spouses[1].id,
                        since = event(record, "MARR").first,
                        status = MarriageStatus.MARRIED,
                    ).ok(),
                )
            }
            record.filter { it.level == 1 && it.tag == "CHIL" }.forEach { childLine ->
                val child = people[childLine.value] ?: return@forEach
                spouses.forEach { parent ->
                    commands += AddRelation(
                        ParentChild.of(RelationId.next(), parent.id, child.id, ParentKind.BIOLOGICAL).ok(),
                    )
                }
            }
            val known = setOf("FAM", "HUSB", "WIFE", "CHIL", "MARR", "DATE", "PLAC", "NOTE")
            skipped += record.map { it.tag }.filterNot(known::contains)
        }
        Outcome.Ok(GedcomPreview(people.size, families.size, skipped, Batch(commands)))
    } catch (failure: IllegalArgumentException) {
        Outcome.Err(DomainError.Storage.Failure(failure.message.orEmpty()))
    }

    fun export(tree: FamilyTree): String {
        val people = tree.persons.values.sortedBy { it.name.sortKey }
        val pointers = people.mapIndexed { index, person -> person.id to "@I${index + 1}@" }.toMap()
        val marriages = tree.relations.values.filterIsInstance<Marriage>().sortedBy { it.id.toString() }
        val parentage = tree.relations.values.filterIsInstance<ParentChild>()
        return buildString {
            appendLine("0 HEAD")
            appendLine("1 SOUR Terevo")
            appendLine("1 GEDC")
            appendLine("2 VERS 5.5.1")
            appendLine("1 CHAR UTF-8")
            people.forEach { person ->
                appendLine("0 ${pointers.getValue(person.id)} INDI")
                appendLine("1 NAME ${person.name.givenName} /${person.name.surname}/")
                appendLine("1 SEX ${person.gender.gedcom}")
                appendEvent("BIRT", person.lifeSpan.birth, person.birthPlace?.title)
                appendEvent("DEAT", person.lifeSpan.death, person.deathPlace?.title)
                person.notes.lineSequence().filter(String::isNotBlank).forEach { appendLine("1 NOTE $it") }
                person.customFields.forEach { (key, value) -> appendLine("1 NOTE $key: $value") }
            }
            val coveredParentage = mutableSetOf<RelationId>()
            marriages.forEachIndexed { index, marriage ->
                appendLine("0 @F${index + 1}@ FAM")
                val first = tree.person(marriage.spouseA)
                val second = tree.person(marriage.spouseB)
                val husband = listOfNotNull(first, second).firstOrNull { it.gender == Gender.MALE } ?: first
                val wife = listOfNotNull(first, second).firstOrNull { it.id != husband?.id }
                husband?.let { appendLine("1 HUSB ${pointers.getValue(it.id)}") }
                wife?.let { appendLine("1 WIFE ${pointers.getValue(it.id)}") }
                appendEvent("MARR", marriage.since, null)
                val commonChildren = tree.childrenOf(marriage.spouseA).intersect(tree.childrenOf(marriage.spouseB).toSet())
                commonChildren.forEach { child ->
                    appendLine("1 CHIL ${pointers.getValue(child)}")
                    coveredParentage += parentage.filter {
                        it.child == child && (it.parent == marriage.spouseA || it.parent == marriage.spouseB)
                    }.map { it.id }
                }
            }
            parentage.filterNot { it.id in coveredParentage }.groupBy { it.parent }.entries
                .sortedBy { it.key.toString() }
                .forEachIndexed { index, (parent, relations) ->
                    appendLine("0 @F${marriages.size + index + 1}@ FAM")
                    val parentPerson = tree.person(parent)
                    val parentTag = if (parentPerson?.gender == Gender.FEMALE) "WIFE" else "HUSB"
                    appendLine("1 $parentTag ${pointers.getValue(parent)}")
                    relations.distinctBy { it.child }.forEach { relation ->
                        appendLine("1 CHIL ${pointers.getValue(relation.child)}")
                    }
                }
            appendLine("0 TRLR")
        }
    }
}

private fun parseLine(value: String): GedcomLine {
    val parts = value.trim().split(Regex("\\s+"), limit = 3)
    require(parts.size >= 2) { "Некорректная строка GEDCOM: $value" }
    val level = parts[0].toInt()
    val second = parts[1]
    val third = parts.getOrElse(2) { "" }
    return if (second.startsWith('@') && second.endsWith('@')) {
        val tail = third.split(Regex("\\s+"), limit = 2)
        GedcomLine(level, second, tail.first(), tail.getOrElse(1) { "" })
    } else {
        GedcomLine(level, null, second, third)
    }
}

private fun records(lines: List<GedcomLine>): List<List<GedcomLine>> {
    val result = mutableListOf<MutableList<GedcomLine>>()
    lines.forEach { line ->
        if (line.level == 0) result += mutableListOf(line) else result.lastOrNull()?.add(line)
    }
    return result
}

private fun parseName(value: String): Pair<String, String> {
    val slash = value.indexOf('/')
    if (slash < 0) return value.trim() to ""
    val end = value.indexOf('/', slash + 1).takeIf { it >= 0 } ?: value.length
    return value.substring(0, slash).trim() to value.substring(slash + 1, end).trim()
}

private fun event(record: List<GedcomLine>, tag: String): Pair<EventDate, String?> {
    val start = record.indexOfFirst { it.level == 1 && it.tag == tag }
    if (start < 0) return EventDate.Unknown to null
    val children = record.drop(start + 1).takeWhile { it.level > 1 }
    val date = children.firstOrNull { it.level == 2 && it.tag == "DATE" }?.value?.let(::parseDate) ?: EventDate.Unknown
    val place = children.firstOrNull { it.level == 2 && it.tag == "PLAC" }?.value
    return date to place
}

private val APPROXIMATE_DATE_PREFIXES = listOf("ABT ", "CAL ", "EST ", "BEF ", "AFT ")

private fun parseDate(value: String): EventDate = try {
    val normalized = value.trim().uppercase()
    if (normalized.startsWith("BET ") && " AND " in normalized) {
        val (from, to) = normalized.removePrefix("BET ").split(" AND ", limit = 2).map(::parseSimpleDate)
        EventDate.Range.of(from.first, to.first).ok()
    } else {
        val prefix = APPROXIMATE_DATE_PREFIXES.firstOrNull { normalized.startsWith(it) }
        val (date, precision) = parseSimpleDate(normalized.removePrefix(prefix.orEmpty()))
        if (prefix != null || precision != DatePrecision.DAY) EventDate.Approximate(date, precision)
        else EventDate.Exact(date)
    }
} catch (malformed: Exception) {
    EventDate.Unknown
}

private fun parseSimpleDate(value: String): Pair<LocalDate, DatePrecision> {
    val parts = value.split(' ')
    val date = when (parts.size) {
        3 -> LocalDate(parts[2].toInt(), MONTHS.getValue(parts[1]), parts[0].toInt())
        2 -> LocalDate(parts[1].toInt(), MONTHS.getValue(parts[0]), 1)
        1 -> LocalDate(parts[0].toInt(), 1, 1)
        else -> throw IllegalArgumentException("Неподдерживаемая дата GEDCOM: $value")
    }
    val precision = when (parts.size) {
        3 -> DatePrecision.DAY
        2 -> DatePrecision.MONTH
        else -> DatePrecision.YEAR
    }
    return date to precision
}

private fun StringBuilder.appendEvent(tag: String, date: EventDate, place: String?) {
    if (date == EventDate.Unknown && place == null) return
    appendLine("1 $tag")
    if (date != EventDate.Unknown) appendLine("2 DATE ${date.gedcom}")
    place?.let { appendLine("2 PLAC $it") }
}

private val EventDate.gedcom: String
    get() = when (this) {
        is EventDate.Exact -> date.gedcom
        is EventDate.Approximate -> "ABT ${around.gedcom}"
        is EventDate.Range -> "BET ${from.gedcom} AND ${to.gedcom}"
        EventDate.Unknown -> ""
    }

private val LocalDate.gedcom: String get() = "$day ${MONTHS.entries.first { it.value == month.ordinal + 1 }.key} $year"

private val Gender.gedcom: String
    get() = when (this) {
        Gender.MALE -> "M"
        Gender.FEMALE -> "F"
        Gender.UNKNOWN -> "U"
    }

private fun <T> Outcome<T>.ok(): T = when (this) {
    is Outcome.Ok -> value
    is Outcome.Err -> throw IllegalArgumentException(error.toString())
}

private val MONTHS = mapOf(
    "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6,
    "JUL" to 7, "AUG" to 8, "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12,
)
