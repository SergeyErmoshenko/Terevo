package me.terevo.gedcom

import me.terevo.domain.model.EventDate
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.Marriage
import me.terevo.domain.model.ParentChild
import me.terevo.testing.shouldBeOk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GedcomCodecTest {

    @Test
    fun `imports people family dates places and sex`() {
        val preview = GedcomCodec.parse(SAMPLE).shouldBeOk()
        val tree = preview.command.applyTo(FamilyTree.EMPTY).shouldBeOk().tree

        assertEquals(3, preview.people)
        assertEquals(1, preview.families)
        assertEquals(3, tree.size)
        assertEquals(1, tree.relations.values.filterIsInstance<Marriage>().size)
        assertEquals(2, tree.relations.values.filterIsInstance<ParentChild>().size)
        assertEquals("Казань", tree.persons.values.first { it.name.givenName == "Иван" }.birthPlace?.title)
    }

    @Test
    fun `export and reimport preserve genealogy semantics`() {
        val original = GedcomCodec.parse(SAMPLE).shouldBeOk().command.applyTo(FamilyTree.EMPTY).shouldBeOk().tree

        val exported = GedcomCodec.export(original)
        val restored = GedcomCodec.parse(exported).shouldBeOk().command.applyTo(FamilyTree.EMPTY).shouldBeOk().tree

        assertEquals(original.persons.values.map { it.name.display }.toSet(), restored.persons.values.map { it.name.display }.toSet())
        assertEquals(
            original.persons.values.associate { it.name.display to it.lifeSpan },
            restored.persons.values.associate { it.name.display to it.lifeSpan },
        )
        assertEquals(
            original.relations.values.groupingBy { it::class.simpleName }.eachCount(),
            restored.relations.values.groupingBy { it::class.simpleName }.eachCount(),
        )
        assertTrue(exported.contains("2 VERS 5.5.1"))
        assertTrue(exported.contains("1 CHAR UTF-8"))
    }

    @Test
    fun `single parent relationship survives export and reimport`() {
        val source = """
            0 @I1@ INDI
            1 NAME Иван /Иванов/
            0 @I2@ INDI
            1 NAME Пётр /Иванов/
            0 @F1@ FAM
            1 HUSB @I1@
            1 CHIL @I2@
            0 TRLR
        """.trimIndent()
        val original = GedcomCodec.parse(source).shouldBeOk().command.applyTo(FamilyTree.EMPTY).shouldBeOk().tree

        val restored = GedcomCodec.parse(GedcomCodec.export(original)).shouldBeOk()
            .command.applyTo(FamilyTree.EMPTY).shouldBeOk().tree

        assertEquals(1, restored.relations.values.filterIsInstance<ParentChild>().size)
    }

    private companion object {
        val SAMPLE = """
            0 HEAD
            1 SOUR Test
            1 CHAR UTF-8
            0 @I1@ INDI
            1 NAME Иван /Иванов/
            1 SEX M
            1 BIRT
            2 DATE 2 OCT 1975
            2 PLAC Казань
            1 DEAT
            2 DATE BET 1 JAN 2020 AND 2 FEB 2020
            0 @I2@ INDI
            1 NAME Анна /Иванова/
            1 SEX F
            0 @I3@ INDI
            1 NAME Пётр /Иванов/
            1 SEX M
            0 @F1@ FAM
            1 HUSB @I1@
            1 WIFE @I2@
            1 CHIL @I3@
            1 MARR
            2 DATE 1 JAN 2000
            0 TRLR
        """.trimIndent()
    }
}
