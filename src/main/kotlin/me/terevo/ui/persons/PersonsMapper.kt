package me.terevo.ui.persons

import kotlinx.datetime.LocalDate
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.interval
import me.terevo.domain.port.MediaRepository
import me.terevo.ui.person.displayText
import me.terevo.ui.person.mainPhotoPath

fun mapPersonRows(tree: FamilyTree, mediaRepository: MediaRepository, today: LocalDate): List<PersonRow> =
    tree.persons.values
        .map { person ->
            val age = if (person.isAlive) person.lifeSpan.ageAt(today) else person.lifeSpan.ageAtDeath
            PersonRow(
                id = person.id,
                thumbnailPath = person.mainPhotoPath(mediaRepository),
                fullName = person.name.display,
                gender = person.gender,
                birthDate = person.lifeSpan.birth.displayText() ?: "—",
                birthDateSortKey = person.lifeSpan.birth.interval?.from,
                residence = person.residence?.title ?: "",
                age = age?.toString() ?: "—",
                ageSortKey = age,
                occupation = person.occupation,
                comment = person.notes,
                alive = person.isAlive,
            )
        }
        .sortedBy { it.fullName }
