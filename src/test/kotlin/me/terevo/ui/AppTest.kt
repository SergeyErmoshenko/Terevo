package me.terevo.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class AppTest {

    @Test
    fun `empty project exposes visible create and open actions in Russian`() {
        assertEquals(
            listOf(
                EmptyProjectAction("Создать проект", AppAction.NewProject),
                EmptyProjectAction("Открыть проект", AppAction.OpenProject),
            ),
            emptyProjectActions,
        )
    }
}
