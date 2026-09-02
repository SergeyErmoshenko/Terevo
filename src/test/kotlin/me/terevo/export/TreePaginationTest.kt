package me.terevo.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.terevo.layout.Layout
import me.terevo.layout.NodeId
import me.terevo.layout.Rect
import me.terevo.layout.Size

class TreePaginationTest {

    @Test
    fun `layout that fits within page size produces a single page with no join labels`() {
        val nodeId = NodeId("a")
        val rect = Rect(left = 0.0, top = 0.0, width = 100.0, height = 100.0)
        val layout = Layout(nodes = mapOf(nodeId to rect), edges = emptyList(), generations = emptyMap(), bounds = rect)
        val labels = mapOf(nodeId to ExportPersonLabel("Иванов Иван", "1900 – 1970", ExportGender.MALE))

        val pages = TreePagination.paginate(layout, labels, Size(width = 200.0, height = 200.0))

        assertEquals(1, pages.size)
        assertEquals(0, pages.single().index)
        assertTrue(pages.single().joinLabels.isEmpty())
    }

    @Test
    fun `node spanning a page boundary gets join labels on both neighboring pages`() {
        val nodeId = NodeId("spanning")
        val rect = Rect(left = 150.0, top = 0.0, width = 100.0, height = 100.0)
        val bounds = Rect(left = 0.0, top = 0.0, width = 300.0, height = 100.0)
        val layout =
            Layout(nodes = mapOf(nodeId to rect), edges = emptyList(), generations = emptyMap(), bounds = bounds)
        val labels = mapOf(nodeId to ExportPersonLabel("Петров Пётр", "1920 – 1990", ExportGender.MALE))

        val pages = TreePagination.paginate(layout, labels, Size(width = 200.0, height = 200.0), overlap = 24.0)

        assertEquals(2, pages.size)
        val firstPage = pages[0]
        val secondPage = pages[1]

        assertEquals(JoinDirection.RIGHT, firstPage.joinLabels.single().direction)
        assertEquals(1, firstPage.joinLabels.single().continuesOnPage)
        assertEquals("Петров Пётр", firstPage.joinLabels.single().personLabel)

        assertEquals(JoinDirection.LEFT, secondPage.joinLabels.single().direction)
        assertEquals(0, secondPage.joinLabels.single().continuesOnPage)
    }

    @Test
    fun `empty layout produces a single empty page`() {
        val pages = TreePagination.paginate(Layout.EMPTY, emptyMap(), Size(width = 200.0, height = 200.0))

        assertEquals(1, pages.size)
        assertEquals(Rect.EMPTY, pages.single().worldBounds)
        assertTrue(pages.single().joinLabels.isEmpty())
    }
}
