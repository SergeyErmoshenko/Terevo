package me.terevo.ui.person

import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.Gender
import me.terevo.domain.model.ParentChild
import me.terevo.testing.person
import me.terevo.testing.shouldBeOk
import kotlin.test.Test
import kotlin.test.assertEquals

class KinshipRolesTest {

    @Test
    fun `roles are resolved from selected person perspective`() {
        val greatGreatGrandmother = person(surname = "Иванова", givenName = "Вера", gender = Gender.FEMALE)
        val greatGrandmother = person(surname = "Иванова", givenName = "Нина", gender = Gender.FEMALE)
        val grandmother = person(surname = "Иванова", givenName = "Анна", gender = Gender.FEMALE)
        val mother = person(surname = "Иванова", givenName = "Мария", gender = Gender.FEMALE)
        val aunt = person(surname = "Петрова", givenName = "Ольга", gender = Gender.FEMALE)
        val son = person(surname = "Иванов", givenName = "Иван", gender = Gender.MALE)
        val daughter = person(surname = "Иванова", givenName = "Елена", gender = Gender.FEMALE)
        val tree = FamilyTree.of(
            persons = listOf(greatGreatGrandmother, greatGrandmother, grandmother, mother, aunt, son, daughter),
            relations = listOf(
                link(greatGreatGrandmother, greatGrandmother),
                link(greatGrandmother, grandmother),
                link(grandmother, mother),
                link(grandmother, aunt),
                link(mother, son),
                link(mother, daughter),
            ),
        ).shouldBeOk()

        val fromSon = KinshipRoles.resolve(tree, son.id).associate { it.person.id to it.role }
        val fromMother = KinshipRoles.resolve(tree, mother.id).associate { it.person.id to it.role }
        val fromAunt = KinshipRoles.resolve(tree, aunt.id).associate { it.person.id to it.role }

        assertEquals("Мама", fromSon[mother.id])
        assertEquals("Бабушка", fromSon[grandmother.id])
        assertEquals("Прабабушка", fromSon[greatGrandmother.id])
        assertEquals("Прапрабабушка", fromSon[greatGreatGrandmother.id])
        assertEquals("Сестра", fromSon[daughter.id])
        assertEquals("Брат", KinshipRoles.resolve(tree, daughter.id).associate { it.person.id to it.role }[son.id])
        assertEquals("Тётя", fromSon[aunt.id])
        assertEquals("Сын", fromMother[son.id])
        assertEquals("Дочь", fromMother[daughter.id])
        assertEquals("Внук", KinshipRoles.resolve(tree, grandmother.id).associate { it.person.id to it.role }[son.id])
        assertEquals("Племянник", fromAunt[son.id])
    }

    private fun link(parent: me.terevo.domain.model.Person, child: me.terevo.domain.model.Person) =
        ParentChild.of(parent = parent.id, child = child.id).shouldBeOk()
}
