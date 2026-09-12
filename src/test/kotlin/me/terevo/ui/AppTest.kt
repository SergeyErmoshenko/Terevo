package me.terevo.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class AppTest {

    @Test
    fun `empty project exposes visible create, open and import actions in Russian`() {
        assertEquals(
            listOf(
                EmptyProjectAction("Создать проект", AppAction.NewProject),
                EmptyProjectAction("Открыть проект", AppAction.OpenProject),
                EmptyProjectAction("Импорт GEDCOM 5.5.1", AppAction.NewProjectFromGedcom),
            ),
            emptyProjectActions,
        )
    }
}
