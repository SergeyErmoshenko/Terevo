package me.terevo.testing

import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.ParentChild
import me.terevo.domain.model.Relation

fun largeFamilyTree(size: Int): FamilyTree {
    val people = List(size) { person(surname = "Фамилия${it % SURNAME_VARIETY}", born = 1800 + it % YEARS) }
    val relations = mutableListOf<Relation>()
    for (index in 1 until size) {
        relations.add(ParentChild.of(parent = people[index / 2].id, child = people[index].id).shouldBeOk())
    }
    return FamilyTree.of(people, relations).shouldBeOk()
}

private const val SURNAME_VARIETY: Int = 25
private const val YEARS: Int = 150
