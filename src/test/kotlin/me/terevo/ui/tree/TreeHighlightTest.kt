package me.terevo.ui.tree

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.terevo.layout.LayoutEdge
import me.terevo.layout.NodeId
import me.terevo.ui.Strings

class TreeHighlightTest {
    private val selected = NodeId("selected")
    private val parent = NodeId("parent")
    private val child = NodeId("child")
    private val spouse = NodeId("spouse")
    private val unrelated = NodeId("unrelated")

    @Test
    fun `selected person is focused and displayed as main`() {
        val highlight = TreeHighlight(selected, mapOf(parent to "Мама"))

        assertEquals(NodeAccent.FOCUSED, highlight.accentOf(selected))
        assertEquals(Strings.MAIN_PERSON, highlight.roleOf(selected))
    }

    @Test
    fun `all resolved relatives are highlighted with their roles`() {
        val highlight = TreeHighlight(
            selected,
            mapOf(
                parent to "Мама",
                child to "Сын",
                spouse to "Жена",
            ),
        )

        assertEquals(NodeAccent.RELATED, highlight.accentOf(parent))
        assertEquals(NodeAccent.RELATED, highlight.accentOf(child))
        assertEquals(NodeAccent.RELATED, highlight.accentOf(spouse))
        assertEquals("Мама", highlight.roleOf(parent))
        assertEquals("Сын", highlight.roleOf(child))
        assertEquals("Жена", highlight.roleOf(spouse))
    }

    @Test
    fun `selected relations are highlighted and unrelated edges are muted`() {
        val highlight = TreeHighlight(selected, mapOf(parent to "Мама"))
        val selectedEdge = LayoutEdge.Parentage(parent, selected)
        val unrelatedEdge = LayoutEdge.Parentage(unrelated, child)

        assertEquals(NodeAccent.RELATED, highlight.accentOf(selectedEdge))
        assertEquals(NodeAccent.MUTED, highlight.accentOf(unrelatedEdge))
    }

    @Test
    fun `unrelated people are muted while no selection keeps everyone neutral`() {
        val highlight = TreeHighlight(selected, mapOf(parent to "Мама"))

        assertEquals(NodeAccent.MUTED, highlight.accentOf(unrelated))
        assertNull(highlight.roleOf(unrelated))
        assertEquals(NodeAccent.NEUTRAL, TreeHighlight.NONE.accentOf(unrelated))
        assertTrue(!TreeHighlight.NONE.isActive)
    }
}
