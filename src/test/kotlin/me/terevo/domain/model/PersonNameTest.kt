package me.terevo.domain.model

import me.terevo.domain.DomainError
import me.terevo.testing.shouldBeErr
import me.terevo.testing.shouldBeOk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonNameTest {

    @Test
    fun `surrounding and repeated whitespace is normalized`() {
        val name = PersonName.of(surname = "  Иванов ", givenName = "Иван   Петр", patronymic = " Ильич ").shouldBeOk()

        assertEquals("Иванов", name.surname)
        assertEquals("Иван Петр", name.givenName)
        assertEquals("Ильич", name.patronymic)
    }

    @Test
    fun `name without surname and given name is rejected`() {
        val error = PersonName.of(surname = "   ", givenName = "").shouldBeErr()

        assertTrue(error is DomainError.Name.Blank)
    }

    @Test
    fun `name with only a given name is accepted`() {
        val name = PersonName.of(surname = "", givenName = "Мария").shouldBeOk()

        assertEquals("Мария", name.display)
    }

    @Test
    fun `display shows maiden name in parentheses`() {
        assertEquals("Иванова (Петрова) Мария", PersonName.of("Иванова", "Мария", maidenName = "Петрова").shouldBeOk().display)
        assertEquals("(Вашкинская) Галина Александровна", PersonName.of("", "Галина", "Александровна", "Вашкинская").shouldBeOk().display)
    }

    @Test
    fun `display skips empty parts`() {
        val name = PersonName.of(surname = "Иванов", givenName = "Иван").shouldBeOk()

        assertEquals("Иванов Иван", name.display)
    }

    @Test
    fun `names with equal parts are equal`() {
        val first = PersonName.of("Иванов", "Иван", "Ильич").shouldBeOk()
        val second = PersonName.of(" Иванов", "Иван ", " Ильич ").shouldBeOk()

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `with revalidates the changed part`() {
        val name = PersonName.of("Иванов", "Иван").shouldBeOk()

        val error = name.with(surname = " ", givenName = "").shouldBeErr()

        assertTrue(error is DomainError.Name.Blank)
    }

    @Test
    fun `maiden name is kept separately from surname`() {
        val name = PersonName.of("Петрова", "Анна", maidenName = "Иванова").shouldBeOk()

        assertEquals("Петрова", name.surname)
        assertEquals("Иванова", name.maidenName)
    }
}
