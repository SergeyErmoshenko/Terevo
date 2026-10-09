package me.terevo.gedcom

import kotlinx.datetime.LocalDate
import me.terevo.domain.command.AddEvent
import me.terevo.domain.command.AddPerson
import me.terevo.domain.command.AddRelation
import me.terevo.domain.command.Batch
import me.terevo.domain.command.Command
import me.terevo.domain.getOrNull
import me.terevo.domain.model.*

internal class GedcomNode(val line: GedcomLine) {
    val tag: String get() = line.tag
    val value: String get() = line.value.trim()
    val children = mutableListOf<GedcomNode>()

    fun child(tag: String): GedcomNode? = children.firstOrNull { it.tag == tag }

    fun all(tag: String): List<GedcomNode> = children.filter { it.tag == tag }

    fun text(): String = buildString {
        append(value)
        children.forEach {
            when (it.tag) {
                "CONT" -> append('\n').append(it.line.value)
                "CONC" -> append(it.line.value)
            }
        }
    }.trim()
}

internal fun buildNode(record: List<GedcomLine>): GedcomNode {
    val root = GedcomNode(record.first())
    val stack = ArrayDeque<GedcomNode>().apply { addLast(root) }
    record.drop(1).forEach { line ->
        while (stack.size > 1 && stack.last().line.level >= line.level) stack.removeLast()
        val node = GedcomNode(line)
        stack.last().children += node
        stack.addLast(node)
    }
    return root
}

private class ImportContext(val notes: Map<String, String>, val sources: Map<String, String>)

private class ImportedPerson(val person: Person, val events: List<Event>, val parentFamilies: Map<String, String?>)

private class ParsedDate(val date: EventDate, val unparsed: String?)

private class RawName(val given: String, val surname: String)

private val PERSON_EVENTS = mapOf(
    "BAPM" to "Крещение",
    "CHR" to "Крещение",
    "CHRA" to "Крещение взрослого",
    "BURI" to "Погребение",
    "CREM" to "Кремация",
    "BARM" to "Бар-мицва",
    "BASM" to "Бат-мицва",
    "BLES" to "Благословение",
    "CONF" to "Конфирмация",
    "FCOM" to "Первое причастие",
    "ORDN" to "Рукоположение",
    "NATU" to "Натурализация",
    "EMIG" to "Эмиграция",
    "IMMI" to "Иммиграция",
    "CENS" to "Перепись",
    "PROB" to "Утверждение завещания",
    "WILL" to "Завещание",
    "GRAD" to "Окончание учёбы",
    "RETI" to "Выход на пенсию",
    "ADOP" to "Усыновление",
    "EVEN" to "Событие",
)

private val FAMILY_EVENTS = mapOf(
    "ENGA" to "Помолвка",
    "MARB" to "Объявление о браке",
    "MARC" to "Брачный договор",
    "MARL" to "Брачная лицензия",
    "MARS" to "Брачное соглашение",
    "DIVF" to "Подача на развод",
    "ANUL" to "Аннулирование брака",
    "CENS" to "Перепись",
    "RESI" to "Проживание семьи",
    "EVEN" to "Событие",
)

private val PERSON_ATTRIBUTES = mapOf(
    "CAST" to "Каста",
    "DSCR" to "Внешность",
    "EDUC" to "Образование",
    "IDNO" to "Идентификационный номер",
    "NATI" to "Национальность",
    "NCHI" to "Количество детей",
    "NMR" to "Количество браков",
    "PROP" to "Имущество",
    "RELI" to "Вероисповедание",
    "SSN" to "Номер соцстрахования",
    "TITL" to "Титул",
    "PHON" to "Телефон",
    "EMAIL" to "Эл. почта",
    "FAX" to "Факс",
    "WWW" to "Сайт",
    "FACT" to "Факт",
)

private val HANDLED_TAGS = setOf(
    "INDI", "FAM", "NAME", "GIVN", "SURN", "SPFX", "NSFX", "NPFX", "NICK", "TYPE", "SEX", "BIRT", "DEAT",
    "DATE", "PLAC", "MAP", "LATI", "LONG", "FORM", "NOTE", "CONT", "CONC", "FAMC", "FAMS", "PEDI", "HUSB",
    "WIFE", "CHIL", "MARR", "DIV", "ADDR", "ADR1", "ADR2", "ADR3", "CITY", "STAE", "POST", "CTRY", "OCCU",
    "RESI", "SOUR", "PAGE", "CAUS", "AGNC", "AGE",
) + PERSON_EVENTS.keys + FAMILY_EVENTS.keys + PERSON_ATTRIBUTES.keys

private val SILENT_TAGS = setOf("CHAN", "TIME", "_UID", "UID", "RIN", "RFN", "AFN", "REFN")

private val MAIDEN_NAME_TYPES = setOf("maiden", "birth", "birth name", "maiden name")

private val ALIAS_NAME_TYPES = setOf("aka", "alias", "also known as", "nickname", "religious", "immigrant", "other")

private val UNKNOWN_DATES = setOf("UNKNOWN", "?", "Y", "N", "НЕИЗВЕСТНО")

private val PATRONYMIC = Regex("(?iu).*(ович|евич|ьич|ич|овна|евна|ична|инична|оглы|кызы|улы)$")

private val SURNAME_WITH_MAIDEN = Regex("""^(.+?)\s*\((.+)\)$""")

private val ONLY_MAIDEN = Regex("""^\(([^()]+)\)$""")

private val LEADING_MAIDEN = Regex("""^\(([^()]+)\)\s*(.*)$""")

internal fun importGedcom(text: String): GedcomPreview {
    val lines = text.lineSequence().filter(String::isNotBlank).map(::parseLine).toList()
    val records = records(lines)
    val context = ImportContext(sharedNotes(records), sharedSources(records))
    val imported = linkedMapOf<String, ImportedPerson>()
    records.filter { it.first().tag == "INDI" }.forEach { record ->
        val pointer = record.first().pointer ?: return@forEach
        imported[pointer] = importPerson(buildNode(record), context)
    }

    val relations = mutableListOf<Command>()
    val events = imported.values.flatMap { it.events }.toMutableList()
    val marriagePairs = mutableSetOf<Set<PersonId>>()
    val parentLinks = mutableSetOf<Pair<PersonId, PersonId>>()
    val families = records.filter { it.first().tag == "FAM" }
    families.forEach { record ->
        val family = buildNode(record)
        val familyPointer = record.first().pointer
        val spouses = listOfNotNull(
            family.child("HUSB")?.value?.let(imported::get),
            family.child("WIFE")?.value?.let(imported::get),
        ).map { it.person }.distinctBy { it.id }

        if (spouses.size == 2) {
            val marriage = marriageOf(family, spouses[0], spouses[1])
            if (marriagePairs.add(setOf(spouses[0].id, spouses[1].id))) relations += AddRelation(marriage)
        }
        family.all("CHIL").forEach { childNode ->
            val child = imported[childNode.value]?.person ?: return@forEach
            val pedigree = childNode.child("PEDI")?.value
                ?: familyPointer?.let { imported[childNode.value]?.parentFamilies?.get(it) }
            val kind = parentKind(pedigree)
            spouses.forEach { parent ->
                if (parent.id != child.id && parentLinks.add(parent.id to child.id)) {
                    relations += AddRelation(ParentChild.of(RelationId.next(), parent.id, child.id, kind).ok())
                }
            }
        }
        events += familyEvents(family, spouses, context)
    }

    val skipped = records.filter { it.first().tag == "INDI" || it.first().tag == "FAM" }
        .flatten().map { it.tag }.filterNot { it in HANDLED_TAGS || it in SILENT_TAGS }.toSet()
    val commands = imported.values.map { AddPerson(it.person) as Command } + relations + events.map(::AddEvent)
    return GedcomPreview(imported.size, families.size, skipped, Batch(commands))
}

private fun sharedNotes(records: List<List<GedcomLine>>): Map<String, String> = records
    .filter { it.first().tag == "NOTE" && it.first().pointer != null }
    .associate { it.first().pointer!! to buildNode(it).text() }

private fun sharedSources(records: List<List<GedcomLine>>): Map<String, String> = records
    .filter { it.first().tag == "SOUR" && it.first().pointer != null }
    .associate { record ->
        val node = buildNode(record)
        val title = node.child("TITL")?.text() ?: node.child("ABBR")?.text() ?: node.value
        record.first().pointer!! to title
    }

private fun importPerson(node: GedcomNode, context: ImportContext): ImportedPerson {
    val id = PersonId.next()
    val fields = linkedMapOf<String, String>()
    fun field(key: String, value: String?) {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return
        val existing = fields[key]
        if (existing == null) fields[key] = text
        else if (text !in existing.split("; ")) fields[key] = "$existing; $text"
    }

    val gender = when (node.child("SEX")?.value?.firstOrNull()?.uppercaseChar()) {
        'M' -> Gender.MALE
        'F' -> Gender.FEMALE
        else -> Gender.UNKNOWN
    }

    val nameNodes = node.all("NAME")
    val maidenNode = nameNodes.firstOrNull { it.nameType() in MAIDEN_NAME_TYPES }
    val mainNode = nameNodes.firstOrNull { it !== maidenNode && it.nameType() !in ALIAS_NAME_TYPES }
        ?: maidenNode
        ?: nameNodes.firstOrNull()
    val main = mainNode?.let(::readName) ?: RawName("", "")
    nameNodes.forEach { name ->
        field("Прозвище", name.child("NICK")?.value)
        field("Суффикс имени", name.child("NSFX")?.value)
        field("Титул", name.child("NPFX")?.value)
        if (name !== mainNode && name !== maidenNode) {
            val other = readName(name)
            field("Другое имя", listOf(other.surname, other.given).filter(String::isNotBlank).joinToString(" "))
        }
    }

    var surname = main.surname
    var givenText = main.given
    var maiden = ""
    if (gender == Gender.FEMALE) {
        if (maidenNode != null && maidenNode !== mainNode) maiden = readName(maidenNode).surname
        if (maiden.isEmpty()) {
            val onlyMaiden = ONLY_MAIDEN.matchEntire(surname)
            val withMaiden = SURNAME_WITH_MAIDEN.matchEntire(surname)
            val leadingInGiven = LEADING_MAIDEN.matchEntire(main.given.trim())
            when {
                onlyMaiden != null -> {
                    surname = ""
                    maiden = onlyMaiden.groupValues[1].trim()
                }

                withMaiden != null -> {
                    surname = withMaiden.groupValues[1].trim()
                    maiden = withMaiden.groupValues[2].trim()
                }

                surname.isBlank() && leadingInGiven != null -> {
                    maiden = leadingInGiven.groupValues[1].trim()
                    givenText = leadingInGiven.groupValues[2].trim()
                }
            }
        }
        if (maiden == surname) maiden = ""
    } else if (maidenNode != null && maidenNode !== mainNode) {
        field("Фамилия при рождении", readName(maidenNode).surname)
    }
    val (given, patronymic) = splitPatronymic(givenText)
    val safeGiven = if (surname.isBlank()) given.ifBlank { "Без имени" } else given
    val name = PersonName.of(surname, safeGiven, patronymic, maiden).ok()

    val birthNode = primary(node, "BIRT")
    val deathNode = primary(node, "DEAT")
    val birth = birthNode?.let(::dateOf) ?: ParsedDate(EventDate.Unknown, null)
    val death = deathNode?.let(::dateOf) ?: ParsedDate(EventDate.Unknown, null)
    val lifeSpan = LifeSpan.of(birth.date, death.date).getOrNull() ?: run {
        field("Дата смерти (как в файле)", deathNode?.child("DATE")?.value)
        LifeSpan.of(birth.date, EventDate.Unknown).ok()
    }
    field("Дата рождения (как в файле)", birth.unparsed)
    field("Дата смерти (как в файле)", death.unparsed)
    field("Причина смерти", deathNode?.child("CAUS")?.text())
    birthNode?.noteTexts(context)?.forEach { field("Заметка о рождении", it) }
    deathNode?.noteTexts(context)?.forEach { field("Заметка о смерти", it) }
    birthNode?.sourceTexts(context)?.forEach { field("Источник", it) }
    deathNode?.sourceTexts(context)?.forEach { field("Источник", it) }

    val residenceNodes = node.all("RESI")
    val residenceCandidates = residenceNodes.mapNotNull { resi -> residencePlace(resi)?.let { resi to it } }
    val mainResidence = residenceCandidates.withIndex()
        .sortedWith(
            compareBy<IndexedValue<Pair<GedcomNode, Place>>>(
                { dateOf(it.value.first).date.interval?.from ?: LocalDate(1, 1, 1) },
                { it.index },
            ),
        )
        .lastOrNull()?.value
    val personAddress = node.child("ADDR")?.let(::addressPlace)
    val residence = mainResidence?.second ?: personAddress
    if (mainResidence != null) field("Адрес", personAddress?.title)

    PERSON_ATTRIBUTES.forEach { (tag, label) ->
        node.all(tag).forEach { attribute ->
            field(if (tag == "FACT") attribute.child("TYPE")?.value?.ifBlank { null } ?: label else label, attributeValue(attribute))
        }
    }
    node.sourceTexts(context).forEach { field("Источник", it) }

    val occupation = node.all("OCCU").map { it.text() }.filter(String::isNotBlank).distinct().joinToString("; ")
    val notes = node.noteTexts(context).joinToString("\n")

    val person = Person.create(
        id = id,
        name = name,
        gender = gender,
        lifeSpan = lifeSpan,
        birthPlace = birthNode?.let(::placeOf),
        deathPlace = deathNode?.let(::placeOf),
        residence = residence,
        occupation = occupation,
        notes = notes,
        customFields = fields.filterKeys(String::isNotBlank),
    ).ok()

    val participants = listOf(EventParticipant(id, "Участник"))
    val events = mutableListOf<Event>()
    PERSON_EVENTS.forEach { (tag, defaultType) ->
        node.all(tag).forEach { eventNode ->
            val type = if (tag == "EVEN") eventNode.child("TYPE")?.value?.ifBlank { null } ?: defaultType else defaultType
            buildEvent(type, eventNode, participants, context)?.let(events::add)
        }
    }
    residenceNodes.forEach { resi ->
        val place = residencePlace(resi)
        val isMain = mainResidence != null && resi === mainResidence.first
        val event = buildEvent("Проживание", resi, participants, context, place, includeValue = false)
        if (event != null && (!isMain || event.date != EventDate.Unknown || event.notes.isNotEmpty())) events += event
    }

    val parentFamilies = node.all("FAMC").filter { it.value.isNotBlank() }.associate { it.value to it.child("PEDI")?.value }
    return ImportedPerson(person, events, parentFamilies)
}

private fun marriageOf(family: GedcomNode, first: Person, second: Person): Marriage {
    val marriageNode = family.child("MARR")
    val divorceNode = family.child("DIV")
    val since = marriageNode?.let(::dateOf)?.date ?: EventDate.Unknown
    val until = divorceNode?.let(::dateOf)?.date ?: EventDate.Unknown
    val status = if (divorceNode != null || family.child("ANUL") != null) MarriageStatus.DIVORCED else MarriageStatus.MARRIED
    val place = marriageNode?.let(::placeOf)
    return Marriage.of(
        id = RelationId.next(),
        first = first.id,
        second = second.id,
        since = since,
        until = until,
        status = status,
        place = place,
    ).getOrNull() ?: Marriage.of(
        id = RelationId.next(),
        first = first.id,
        second = second.id,
        since = since,
        status = status,
        place = place,
    ).ok()
}

private fun familyEvents(family: GedcomNode, spouses: List<Person>, context: ImportContext): List<Event> {
    if (spouses.isEmpty()) return emptyList()
    val participants = spouses.map { EventParticipant(it.id, spouseRole(it.gender)) }
    val events = mutableListOf<Event>()
    val married = spouses.size == 2
    family.all("MARR").forEach { node ->
        buildEvent("Брак", node, participants, context)
            ?.takeIf { !married || it.notes.isNotEmpty() }?.let(events::add)
    }
    family.all("DIV").forEach { node ->
        buildEvent("Развод", node, participants, context)
            ?.takeIf { !married || it.notes.isNotEmpty() }?.let(events::add)
    }
    FAMILY_EVENTS.forEach { (tag, defaultType) ->
        family.all(tag).forEach { node ->
            val type = if (tag == "EVEN") node.child("TYPE")?.value?.ifBlank { null } ?: defaultType else defaultType
            buildEvent(type, node, participants, context, includeValue = tag != "RESI")?.let(events::add)
        }
    }
    return events
}

private fun spouseRole(gender: Gender): String = when (gender) {
    Gender.MALE -> "Муж"
    Gender.FEMALE -> "Жена"
    Gender.UNKNOWN -> "Супруг"
}

private fun buildEvent(
    type: String,
    node: GedcomNode,
    participants: List<EventParticipant>,
    context: ImportContext,
    place: Place? = placeOf(node),
    includeValue: Boolean = true,
): Event? {
    val date = dateOf(node)
    val notes = buildList {
        if (includeValue) node.text().takeIf { it.isNotBlank() && !it.equals("Y", ignoreCase = true) }?.let(::add)
        if (node.tag != "EVEN") node.child("TYPE")?.value?.takeIf(String::isNotBlank)?.let { add("Тип: $it") }
        node.child("CAUS")?.text()?.takeIf(String::isNotBlank)?.let { add("Причина: $it") }
        node.child("AGNC")?.text()?.takeIf(String::isNotBlank)?.let { add("Организация: $it") }
        node.child("AGE")?.value?.takeIf(String::isNotBlank)?.let { add("Возраст: $it") }
        date.unparsed?.let { add("Дата в файле: $it") }
        addAll(node.noteTexts(context))
        node.sourceTexts(context).forEach { add("Источник: $it") }
    }.joinToString("\n")
    return Event.of(type = type, date = date.date, place = place, participants = participants, notes = notes).getOrNull()
}

private fun GedcomNode.nameType(): String = child("TYPE")?.value?.lowercase().orEmpty()

private fun primary(node: GedcomNode, tag: String): GedcomNode? {
    val candidates = node.all(tag)
    return candidates.firstOrNull { it.child("DATE") != null || it.child("PLAC") != null } ?: candidates.firstOrNull()
}

private fun readName(node: GedcomNode): RawName {
    val (givenFromValue, surnameFromValue) = parseName(node.line.value)
    val given = node.child("GIVN")?.value?.takeIf(String::isNotBlank) ?: givenFromValue
    val core = node.child("SURN")?.value?.takeIf(String::isNotBlank) ?: surnameFromValue
    val surname = listOfNotNull(node.child("SPFX")?.value?.takeIf(String::isNotBlank), core.takeIf(String::isNotBlank))
        .joinToString(" ")
    return RawName(given, surname)
}

private fun splitPatronymic(given: String): Pair<String, String> {
    val parts = given.split(' ').filter(String::isNotBlank)
    return if (parts.size >= 2 && PATRONYMIC.matches(parts.last())) {
        parts.dropLast(1).joinToString(" ") to parts.last()
    } else {
        given to ""
    }
}

private fun dateOf(node: GedcomNode): ParsedDate {
    val raw = node.child("DATE")?.value.orEmpty()
    val parsed = parseDate(raw)
    val unparsed = raw.takeIf { parsed == null && it.isNotBlank() && it.uppercase() !in UNKNOWN_DATES }
    return ParsedDate(parsed ?: EventDate.Unknown, unparsed)
}

private fun placeOf(node: GedcomNode): Place? {
    val place = node.child("PLAC") ?: return null
    val map = place.child("MAP")
    val latitude = coordinate(map?.child("LATI")?.value, "S")
    val longitude = coordinate(map?.child("LONG")?.value, "W")
    val coordinates = if (
        latitude != null && longitude != null && latitude in -90.0..90.0 && longitude in -180.0..180.0
    ) {
        Coordinates(latitude, longitude)
    } else {
        null
    }
    return Place.of(place.value, coordinates).getOrNull()
}

private fun coordinate(raw: String?, negativeLetter: String): Double? {
    val value = raw?.trim()?.uppercase().orEmpty()
    if (value.isEmpty()) return null
    val number = value.trimStart('N', 'S', 'E', 'W').replace(',', '.').toDoubleOrNull() ?: return null
    return if (value.startsWith(negativeLetter)) -number else number
}

private fun addressPlace(node: GedcomNode): Place? {
    val parts = buildList {
        add(node.text().replace("\n", ", "))
        listOf("ADR1", "ADR2", "ADR3", "CITY", "STAE", "POST", "CTRY").forEach { add(node.child(it)?.value.orEmpty()) }
    }.map(String::trim).filter(String::isNotEmpty).distinct()
    return Place.of(parts.joinToString(", ")).getOrNull()
}

private fun residencePlace(node: GedcomNode): Place? =
    placeOf(node) ?: node.child("ADDR")?.let(::addressPlace) ?: node.value.takeIf(String::isNotBlank)
        ?.let { Place.of(it).getOrNull() }

private fun attributeValue(node: GedcomNode): String {
    val base = node.text().takeUnless { it.equals("Y", ignoreCase = true) }.orEmpty()
    val extras = listOf(node.child("DATE")?.value.orEmpty(), node.child("PLAC")?.value.orEmpty())
        .filter(String::isNotBlank).joinToString(", ")
    return when {
        extras.isEmpty() -> base
        base.isEmpty() -> extras
        else -> "$base ($extras)"
    }
}

private fun String.isPointer(): Boolean = length > 2 && startsWith('@') && endsWith('@')

private fun GedcomNode.noteTexts(context: ImportContext): List<String> = all("NOTE")
    .map { if (it.value.isPointer()) context.notes[it.value].orEmpty() else it.text() }
    .filter(String::isNotBlank)

private fun GedcomNode.sourceTexts(context: ImportContext): List<String> = all("SOUR").map { citation ->
    val title = if (citation.value.isPointer()) context.sources[citation.value].orEmpty() else citation.text()
    listOf(title, citation.child("PAGE")?.text().orEmpty()).filter(String::isNotBlank).joinToString(", ")
}.filter(String::isNotBlank)
