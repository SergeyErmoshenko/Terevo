package me.terevo.ui.tree

import me.terevo.domain.model.Gender
import me.terevo.domain.model.PersonId
import me.terevo.layout.Point
import me.terevo.ui.person.RelationMode

data class DragRelationMenuState(
    val source: PersonId,
    val target: PersonId,
    val position: Point,
    val validity: Map<RelationMode, Boolean>,
    val sourceGender: Gender = Gender.UNKNOWN,
)

