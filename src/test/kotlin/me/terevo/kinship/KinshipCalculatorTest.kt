package me.terevo.kinship

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.Gender
import me.terevo.domain.model.Marriage
import me.terevo.domain.model.ParentChild
import me.terevo.domain.model.ParentKind
import me.terevo.domain.model.Person
import me.terevo.domain.model.PersonId
import me.terevo.testing.person
import me.terevo.testing.shouldBeOk

class KinshipCalculatorTest {

    private inner class Fixture {
        val ggGreatGrandpa = person(givenName = "Пётр", gender = Gender.MALE)
        val ggFather = person(givenName = "Игорь", gender = Gender.MALE)
        val ggMother = person(givenName = "Вера", gender = Gender.FEMALE)
        val grandpa = person(givenName = "Николай", gender = Gender.MALE)
        val grandpaBrother = person(givenName = "Степан", gender = Gender.MALE)
        val grandpaCousinBranch = person(givenName = "Фёдор", gender = Gender.MALE)
        val grandma = person(givenName = "Анна", gender = Gender.FEMALE)
        val father = person(givenName = "Сергей", gender = Gender.MALE)
        val auntSister = person(givenName = "Ольга", gender = Gender.FEMALE)
        val auntHusband = person(givenName = "Павел", gender = Gender.MALE)
        val mother = person(givenName = "Мария", gender = Gender.FEMALE)
        val ego = person(surname = "Иванов", givenName = "Иван", gender = Gender.MALE)
        val sister = person(surname = "Иванова", givenName = "Елена", gender = Gender.FEMALE)
        val cousin1 = person(givenName = "Дмитрий", gender = Gender.MALE)
        val cousin1Spouse = person(givenName = "Светлана", gender = Gender.FEMALE)
        val cousin1Child = person(givenName = "Алина", gender = Gender.FEMALE)
        val parentOfSecondCousin = person(givenName = "Борис", gender = Gender.MALE)
        val secondCousin = person(givenName = "Ксения", gender = Gender.FEMALE)
        val egoWife = person(givenName = "Наталья", gender = Gender.FEMALE)
        val egoWifeFather = person(givenName = "Виктор", gender = Gender.MALE)
        val egoWifeMother = person(givenName = "Людмила", gender = Gender.FEMALE)
        val egoWifeBrother = person(givenName = "Артём", gender = Gender.MALE)
        val egoWifeSister = person(givenName = "Дарья", gender = Gender.FEMALE)
        val husband = person(givenName = "Максим", gender = Gender.MALE)
        val husbandFather = person(givenName = "Роман", gender = Gender.MALE)
        val husbandMother = person(givenName = "Татьяна", gender = Gender.FEMALE)
        val husbandBrother = person(givenName = "Кирилл", gender = Gender.MALE)
        val husbandSister = person(givenName = "Юлия", gender = Gender.FEMALE)
        val stepMother2 = person(givenName = "Галина", gender = Gender.FEMALE)
        val stepSibling = person(givenName = "Егор", gender = Gender.MALE)
        val adoptedSibling = person(givenName = "Полина", gender = Gender.FEMALE)
        val unrelatedPerson = person(givenName = "Аноним", gender = Gender.UNKNOWN)

        val tree: FamilyTree = FamilyTree.of(
            persons = listOf(
                ggGreatGrandpa, ggFather, ggMother, grandpa, grandpaBrother, grandpaCousinBranch, grandma,
                father, auntSister, auntHusband, mother, ego, sister, cousin1, cousin1Spouse, cousin1Child,
                parentOfSecondCousin, secondCousin, egoWife, egoWifeFather, egoWifeMother, egoWifeBrother,
                egoWifeSister, husband, husbandFather, husbandMother, husbandBrother, husbandSister,
                stepMother2, stepSibling, adoptedSibling, unrelatedPerson,
            ),
            relations = listOf(
                marriage(ggFather, ggMother),
                parent(ggGreatGrandpa, ggFather),
                parent(ggFather, grandpa),
                parent(ggMother, grandpa),
                parent(ggFather, grandpaBrother),
                parent(ggMother, grandpaBrother),
                parent(ggFather, grandpaCousinBranch),
                parent(ggMother, grandpaCousinBranch),
                parent(grandpaCousinBranch, parentOfSecondCousin),
                parent(parentOfSecondCousin, secondCousin),
                marriage(grandpa, grandma),
                parent(grandpa, father),
                parent(grandma, father),
                parent(grandpa, auntSister),
                parent(grandma, auntSister),
                marriage(father, mother),
                parent(father, ego),
                parent(mother, ego),
                parent(father, sister),
                parent(mother, sister),
                marriage(auntSister, auntHusband),
                parent(auntSister, cousin1),
                parent(auntHusband, cousin1),
                marriage(cousin1, cousin1Spouse),
                parent(cousin1, cousin1Child),
                parent(cousin1Spouse, cousin1Child),
                marriage(ego, egoWife),
                marriage(egoWifeFather, egoWifeMother),
                parent(egoWifeFather, egoWife),
                parent(egoWifeMother, egoWife),
                parent(egoWifeFather, egoWifeBrother),
                parent(egoWifeMother, egoWifeBrother),
                parent(egoWifeFather, egoWifeSister),
                parent(egoWifeMother, egoWifeSister),
                marriage(sister, husband),
                marriage(husbandFather, husbandMother),
                parent(husbandFather, husband),
                parent(husbandMother, husband),
                parent(husbandFather, husbandBrother),
                parent(husbandMother, husbandBrother),
                parent(husbandFather, husbandSister),
                parent(husbandMother, husbandSister),
                marriage(father, stepMother2),
                parent(stepMother2, stepSibling),
                parent(father, stepSibling, ParentKind.STEP),
                parent(father, adoptedSibling, ParentKind.ADOPTIVE),
            ),
        ).shouldBeOk()
    }

    private fun parent(parent: Person, child: Person, kind: ParentKind = ParentKind.BIOLOGICAL) =
        ParentChild.of(parent = parent.id, child = child.id, kind = kind).shouldBeOk()

    private fun marriage(first: Person, second: Person) = Marriage.of(first = first.id, second = second.id).shouldBeOk()

    private fun Fixture.term(a: Person, b: Person): String = when (val result = KinshipCalculator.resolve(tree, a.id, b.id)) {
        is KinshipResult.Blood -> result.term
        is KinshipResult.InLaw -> result.term
        KinshipResult.SamePerson -> "SamePerson"
        KinshipResult.Unrelated -> "Unrelated"
    }

    @Test
    fun `resolve returns SamePerson for identical ids`() {
        val fixture = Fixture()
        assertIs<KinshipResult.SamePerson>(KinshipCalculator.resolve(fixture.tree, fixture.ego.id, fixture.ego.id))
    }

    @Test
    fun `mother resolves to Мама`() {
        val fixture = Fixture()
        assertEquals("Мама", fixture.term(fixture.ego, fixture.mother))
    }

    @Test
    fun `father resolves to Папа`() {
        val fixture = Fixture()
        assertEquals("Папа", fixture.term(fixture.ego, fixture.father))
    }

    @Test
    fun `grandfather resolves to Дедушка`() {
        val fixture = Fixture()
        assertEquals("Дедушка", fixture.term(fixture.ego, fixture.grandpa))
    }

    @Test
    fun `grandmother resolves to Бабушка`() {
        val fixture = Fixture()
        assertEquals("Бабушка", fixture.term(fixture.ego, fixture.grandma))
    }

    @Test
    fun `great grandfather resolves to Прадедушка`() {
        val fixture = Fixture()
        assertEquals("Прадедушка", fixture.term(fixture.ego, fixture.ggFather))
    }

    @Test
    fun `great grandmother resolves to Прабабушка`() {
        val fixture = Fixture()
        assertEquals("Прабабушка", fixture.term(fixture.ego, fixture.ggMother))
    }

    @Test
    fun `great great grandfather resolves to Прапрадедушка`() {
        val fixture = Fixture()
        assertEquals("Прапрадедушка", fixture.term(fixture.ego, fixture.ggGreatGrandpa))
    }

    @Test
    fun `sister resolves to Сестра`() {
        val fixture = Fixture()
        assertEquals("Сестра", fixture.term(fixture.ego, fixture.sister))
    }

    @Test
    fun `brother resolves to Брат from siblings own perspective`() {
        val fixture = Fixture()
        assertEquals("Брат", fixture.term(fixture.sister, fixture.ego))
    }

    @Test
    fun `first cousin resolves to Двоюродный брат`() {
        val fixture = Fixture()
        assertEquals("Двоюродный брат", fixture.term(fixture.ego, fixture.cousin1))
    }

    @Test
    fun `second cousin resolves to Троюродная сестра`() {
        val fixture = Fixture()
        assertEquals("Троюродная сестра", fixture.term(fixture.ego, fixture.secondCousin))
    }

    @Test
    fun `grandfathers brother resolves to Двоюродный дедушка`() {
        val fixture = Fixture()
        assertEquals("Двоюродный дедушка", fixture.term(fixture.ego, fixture.grandpaBrother))
    }

    @Test
    fun `grand nephew resolves to Внучатый племянник`() {
        val fixture = Fixture()
        assertEquals("Внучатый племянник", fixture.term(fixture.grandpaBrother, fixture.ego))
    }

    @Test
    fun `fathers sister resolves to Тётя`() {
        val fixture = Fixture()
        assertEquals("Тётя", fixture.term(fixture.ego, fixture.auntSister))
    }

    @Test
    fun `nephew resolves to Племянник from aunts perspective`() {
        val fixture = Fixture()
        assertEquals("Племянник", fixture.term(fixture.auntSister, fixture.ego))
    }

    @Test
    fun `first cousins child resolves to Двоюродная племянница`() {
        val fixture = Fixture()
        assertEquals("Двоюродная племянница", fixture.term(fixture.ego, fixture.cousin1Child))
    }

    @Test
    fun `first cousin once removed resolves to Двоюродный дядя`() {
        val fixture = Fixture()
        assertEquals("Двоюродный дядя", fixture.term(fixture.cousin1Child, fixture.ego))
    }

    @Test
    fun `spouse resolves to Жена`() {
        val fixture = Fixture()
        assertEquals("Жена", fixture.term(fixture.ego, fixture.egoWife))
    }

    @Test
    fun `spouse resolves to Муж from wifes perspective`() {
        val fixture = Fixture()
        assertEquals("Муж", fixture.term(fixture.egoWife, fixture.ego))
    }

    @Test
    fun `wifes father resolves to Тесть`() {
        val fixture = Fixture()
        assertEquals("Тесть", fixture.term(fixture.ego, fixture.egoWifeFather))
    }

    @Test
    fun `wifes mother resolves to Тёща`() {
        val fixture = Fixture()
        assertEquals("Тёща", fixture.term(fixture.ego, fixture.egoWifeMother))
    }

    @Test
    fun `wifes brother resolves to Шурин`() {
        val fixture = Fixture()
        assertEquals("Шурин", fixture.term(fixture.ego, fixture.egoWifeBrother))
    }

    @Test
    fun `wifes sister resolves to Свояченица`() {
        val fixture = Fixture()
        assertEquals("Свояченица", fixture.term(fixture.ego, fixture.egoWifeSister))
    }

    @Test
    fun `husbands father resolves to Свёкор`() {
        val fixture = Fixture()
        assertEquals("Свёкор", fixture.term(fixture.sister, fixture.husbandFather))
    }

    @Test
    fun `husbands mother resolves to Свекровь`() {
        val fixture = Fixture()
        assertEquals("Свекровь", fixture.term(fixture.sister, fixture.husbandMother))
    }

    @Test
    fun `husbands brother resolves to Деверь`() {
        val fixture = Fixture()
        assertEquals("Деверь", fixture.term(fixture.sister, fixture.husbandBrother))
    }

    @Test
    fun `husbands sister resolves to Золовка`() {
        val fixture = Fixture()
        assertEquals("Золовка", fixture.term(fixture.sister, fixture.husbandSister))
    }

    @Test
    fun `sisters husband resolves to Зять`() {
        val fixture = Fixture()
        assertEquals("Зять", fixture.term(fixture.ego, fixture.husband))
    }

    @Test
    fun `sons wife resolves to Невестка from aunts perspective`() {
        val fixture = Fixture()
        assertEquals("Невестка", fixture.term(fixture.auntSister, fixture.cousin1Spouse))
    }

    @Test
    fun `step sibling resolves to сводный брат`() {
        val fixture = Fixture()
        assertEquals("сводный брат", fixture.term(fixture.ego, fixture.stepSibling))
    }

    @Test
    fun `adopted sibling resolves to приёмная сестра`() {
        val fixture = Fixture()
        assertEquals("приёмная сестра", fixture.term(fixture.ego, fixture.adoptedSibling))
    }

    @Test
    fun `unrelated person resolves to Unrelated`() {
        val fixture = Fixture()
        assertIs<KinshipResult.Unrelated>(KinshipCalculator.resolve(fixture.tree, fixture.ego.id, fixture.unrelatedPerson.id))
    }

    @Test
    fun `fallback beyond table returns nth degree wording`() {
        val root = person(givenName = "Родоначальник", gender = Gender.MALE)
        val branchA = chain(root, 4)
        val branchB = chain(root, 4)
        val tree = FamilyTree.of(
            persons = listOf(root) + branchA + branchB,
            relations = link(root, branchA.first()) +
                chainLinks(branchA) +
                link(root, branchB.first()) +
                chainLinks(branchB),
        ).shouldBeOk()
        val result = KinshipCalculator.resolve(tree, branchA.last().id, branchB.last().id)
        assertEquals("родственник в 8-м колене", (result as KinshipResult.Blood).term)
    }

    private fun chain(root: Person, length: Int): List<Person> =
        (1..length).map { person(givenName = "${root.name.givenName}_$it", gender = Gender.UNKNOWN) }

    private fun chainLinks(chain: List<Person>): List<ParentChild> =
        chain.zipWithNext().map { (parentPerson, childPerson) -> parent(parentPerson, childPerson) }

    private fun link(parentPerson: Person, childPerson: Person): List<ParentChild> = listOf(parent(parentPerson, childPerson))
}
