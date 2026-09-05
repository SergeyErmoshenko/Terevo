package me.terevo.ui.persons

import kotlinx.datetime.LocalDate
import me.terevo.domain.model.Gender
import me.terevo.domain.model.PersonId

data class PersonRow(
    val id: PersonId,
    val thumbnailPath: String?,
    val fullName: String,
    val gender: Gender,
    val birthDate: String,
    val birthDateSortKey: LocalDate?,
    val residence: String,
    val age: String,
    val ageSortKey: Int?,
    val occupation: String,
    val comment: String,
    val alive: Boolean,
)
