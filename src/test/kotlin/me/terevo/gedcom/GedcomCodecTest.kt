package me.terevo.gedcom

import me.terevo.domain.model.EventDate
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.Marriage
import me.terevo.domain.model.MarriageStatus
import me.terevo.domain.model.ParentChild
import me.terevo.domain.model.ParentKind
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

        assertEquals(
            original.persons.values.map { it.name.display }.toSet(),
            restored.persons.values.map { it.name.display }.toSet()
        )
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
    fun `person comment is imported from inline NOTE with continuations and from referenced NOTE record`() {
        val source = """
            0 @I1@ INDI
            1 NAME Иван /Иванов/
            1 NOTE Первая строка
            2 CONT вторая строка
            2 CONC , продолжение
            1 NOTE @N1@
            0 @I2@ INDI
            1 NAME Анна /Иванова/
            0 @N1@ NOTE Общая заметка
            1 CONT на две строки
            0 TRLR
        """.trimIndent()

        val preview = GedcomCodec.parse(source).shouldBeOk()
        val tree = preview.command.applyTo(FamilyTree.EMPTY).shouldBeOk().tree

        assertEquals(
            "Первая строка\nвторая строка, продолжение\nОбщая заметка\nна две строки",
            tree.persons.values.first { it.name.givenName == "Иван" }.notes,
        )
        assertEquals("", tree.persons.values.first { it.name.givenName == "Анна" }.notes)
        assertTrue("CONT" !in preview.skippedTags && "CONC" !in preview.skippedTags)
    }

    @Test
    fun `imports occupation residence with coordinates patronymic and maiden name`() {
        val source = """
            0 @I1@ INDI
            1 NAME Мария Ивановна /Петрова/
            2 NICK Маша
            1 NAME Мария /Сидорова/
            2 TYPE maiden
            1 SEX F
            1 OCCU Учитель
            1 OCCU Библиотекарь
            1 RESI
            2 DATE 1990
            2 PLAC Москва
            3 MAP
            4 LATI N55.75
            4 LONG E37.62
            1 RESI
            2 DATE 2005
            2 PLAC Казань
            1 RELI Православная
            1 EDUC Педагогический институт
            2 DATE 1980
            0 @I2@ INDI
            1 NAME Анна /Иванова (Смирнова)/
            1 SEX F
            0 TRLR
        """.trimIndent()

        val tree = GedcomCodec.parse(source).shouldBeOk().command.applyTo(FamilyTree.EMPTY).shouldBeOk().tree
        val maria = tree.persons.values.first { it.name.givenName == "Мария" }

        assertEquals("Петрова", maria.name.surname)
        assertEquals("Ивановна", maria.name.patronymic)
        assertEquals("Сидорова", maria.name.maidenName)
        assertEquals("Учитель; Библиотекарь", maria.occupation)
        assertEquals("Казань", maria.residence?.title)
        assertEquals("Маша", maria.customFields["Прозвище"])
        assertEquals("Православная", maria.customFields["Вероисповедание"])
        assertEquals("Педагогический институт (1980)", maria.customFields["Образование"])
        val moscow = tree.events.values.first { it.place?.title == "Москва" }
        assertEquals("Проживание", moscow.type)
        assertEquals(55.75, moscow.place?.coordinates?.latitude)
        assertEquals(37.62, moscow.place?.coordinates?.longitude)
        val anna = tree.persons.values.first { it.name.givenName == "Анна" }
        assertEquals("Иванова", anna.name.surname)
        assertEquals("Смирнова", anna.name.maidenName)
    }

    @Test
    fun `surname written in parentheses becomes maiden name and survives export`() {
        val source = """
            0 @I1@ INDI
            1 NAME Галина Александровна /(Вашкинская)/
            1 SEX F
            0 @I2@ INDI
            1 NAME /(Лапшина)/
            1 SEX F
            0 @I3@ INDI
            1 NAME (Косаковская) Зоя
            1 SEX F
            0 TRLR
        """.trimIndent()

        val tree = GedcomCodec.parse(source).shouldBeOk().command.applyTo(FamilyTree.EMPTY).shouldBeOk().tree
        val names = tree.persons.values.map { it.name.display }.toSet()

        assertEquals(
            setOf("(Вашкинская) Галина Александровна", "(Лапшина) Без имени", "(Косаковская) Зоя"),
            names,
        )
        val galina = tree.persons.values.first { it.name.givenName == "Галина" }
        assertEquals("", galina.name.surname)
        assertEquals("Вашкинская", galina.name.maidenName)

        val restored = GedcomCodec.parse(GedcomCodec.export(tree)).shouldBeOk()
            .command.applyTo(FamilyTree.EMPTY).shouldBeOk().tree
        assertEquals(names, restored.persons.values.map { it.name.display }.toSet())
    }

    @Test
    fun `imports person events marriage end place and child side pedigree`() {
        val source = """
            0 @I1@ INDI
            1 NAME Иван /Иванов/
            1 SEX M
            1 BURI
            2 DATE 5 MAR 1999
            2 PLAC Кладбище
            1 FAMS @F1@
            0 @I2@ INDI
            1 NAME Анна /Иванова/
            1 SEX F
            0 @I3@ INDI
            1 NAME Пётр /Иванов/
            1 SEX M
            1 FAMC @F1@
            2 PEDI adopted
            0 @F1@ FAM
            1 HUSB @I1@
            1 WIFE @I2@
            1 CHIL @I3@
            1 MARR
            2 DATE 1 JAN 1970
            2 PLAC Рига
            1 DIV
            2 DATE 2 FEB 1980
            0 TRLR
        """.trimIndent()

        val tree = GedcomCodec.parse(source).shouldBeOk().command.applyTo(FamilyTree.EMPTY).shouldBeOk().tree
        val marriage = tree.relations.values.filterIsInstance<Marriage>().single()

        assertEquals(MarriageStatus.DIVORCED, marriage.status)
        assertEquals("Рига", marriage.place?.title)
        assertTrue(marriage.until != EventDate.Unknown)
        assertEquals(
            setOf(ParentKind.ADOPTIVE),
            tree.relations.values.filterIsInstance<ParentChild>().map { it.kind }.toSet(),
        )
        val burial = tree.events.values.single()
        assertEquals("Погребение", burial.type)
        assertEquals("Кладбище", burial.place?.title)
    }

    @Test
    fun `date formats from other programs are parsed and unparsable dates are kept as custom field`() {
        val source = """
            0 @I1@ INDI
            1 NAME Иван /Иванов/
            1 BIRT
            2 DATE 17.05.1986
            1 DEAT
            2 DATE около лета 2001
            0 TRLR
        """.trimIndent()

        val tree = GedcomCodec.parse(source).shouldBeOk().command.applyTo(FamilyTree.EMPTY).shouldBeOk().tree
        val person = tree.persons.values.single()

        assertTrue(person.lifeSpan.birth is EventDate.Exact)
        assertEquals("около лета 2001", person.customFields["Дата смерти (как в файле)"])
    }

    @Test
    fun `third-party date qualifiers BEF AFT CAL EST parse as approximate instead of crashing`() {
        val source = """
            0 @I1@ INDI
            1 NAME Иван /Иванов/
            1 BIRT
            2 DATE BEF 1950
            0 @I2@ INDI
            1 NAME Пётр /Петров/
            1 DEAT
            2 DATE AFT 1980
            0 @I3@ INDI
            1 NAME Анна /Иванова/
            1 BIRT
            2 DATE CAL 1900
            0 @I4@ INDI
            1 NAME Мария /Петрова/
            1 BIRT
            2 DATE EST 1920
            0 TRLR
        """.trimIndent()
        val tree = GedcomCodec.parse(source).shouldBeOk().command.applyTo(FamilyTree.EMPTY).shouldBeOk().tree

        assertTrue(tree.persons.values.first { it.name.givenName == "Иван" }.lifeSpan.birth is EventDate.Approximate)
        assertTrue(tree.persons.values.first { it.name.givenName == "Пётр" }.lifeSpan.death is EventDate.Approximate)
        assertTrue(tree.persons.values.first { it.name.givenName == "Анна" }.lifeSpan.birth is EventDate.Approximate)
        assertTrue(tree.persons.values.first { it.name.givenName == "Мария" }.lifeSpan.birth is EventDate.Approximate)
    }

    @Test
    fun `unsupported date text falls back to unknown instead of crashing`() {
        val source = """
            0 @I1@ INDI
            1 NAME Иван /Иванов/
            1 BIRT
            2 DATE НЕПОНЯТНО ЧТО
            0 TRLR
        """.trimIndent()
        val tree = GedcomCodec.parse(source).shouldBeOk().command.applyTo(FamilyTree.EMPTY).shouldBeOk().tree

        assertEquals(EventDate.Unknown, tree.persons.values.first().lifeSpan.birth)
    }

    @Test
    fun `child in blended family with PEDI step does not violate biological parent limit`() {
        val source = """
            0 @I1@ INDI
            1 NAME Иван /Иванов/
            0 @I2@ INDI
            1 NAME Анна /Иванова/
            0 @I3@ INDI
            1 NAME Пётр /Петров/
            0 @I4@ INDI
            1 NAME Сергей /Сергеев/
            0 @F1@ FAM
            1 HUSB @I1@
            1 WIFE @I2@
            1 CHIL @I4@
            0 @F2@ FAM
            1 HUSB @I3@
            1 CHIL @I4@
            2 PEDI STEP
            0 TRLR
        """.trimIndent()
        val preview = GedcomCodec.parse(source).shouldBeOk()
        val tree = preview.command.applyTo(FamilyTree.EMPTY).shouldBeOk().tree

        val child = tree.persons.values.first { it.name.givenName == "Сергей" }
        assertEquals(3, tree.parentsOf(child.id).size)
        assertEquals(2, tree.biologicalParentsOf(child.id).size)
    }

    @Test
    fun `PEDI adopted and foster tags map to non-biological parent kinds`() {
        val source = """
            0 @I1@ INDI
            1 NAME Иван /Иванов/
            0 @I2@ INDI
            1 NAME Мария /Иванова/
            0 @F1@ FAM
            1 HUSB @I1@
            1 CHIL @I2@
            2 PEDI ADOPTED
            0 TRLR
        """.trimIndent()
        val tree = GedcomCodec.parse(source).shouldBeOk().command.applyTo(FamilyTree.EMPTY).shouldBeOk().tree

        val child = tree.persons.values.first { it.name.givenName == "Мария" }
        assertTrue(tree.biologicalParentsOf(child.id).isEmpty())
        assertEquals(1, tree.parentsOf(child.id).size)
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
