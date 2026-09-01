package me.terevo.ui.tree

import me.terevo.layout.LayoutEdge
import me.terevo.layout.NodeId
import me.terevo.layout.endpoints
import me.terevo.ui.Strings

enum class PersonVisualGender {
    MALE,
    FEMALE,
    UNKNOWN,
}

data class PersonVisual(
    val id: NodeId,
    val name: String,
    val lifeYears: String,
    val gender: PersonVisualGender,
    val version: Long,
)

data class TreeVisuals(
    val persons: Map<NodeId, PersonVisual> = emptyMap(),
)

enum class NodeAccent {
    NEUTRAL,
    FOCUSED,
    RELATED,
    MUTED,
}

data class TreeHighlight(
    val selected: NodeId? = null,
    val roles: Map<NodeId, String> = emptyMap(),
) {
    val isActive: Boolean get() = selected != null

    fun isRelated(id: NodeId): Boolean = id == selected || id in roles

    fun roleOf(id: NodeId): String? = when {
        !isActive -> null
        id == selected -> Strings.MAIN_PERSON
        else -> roles[id]
    }

    fun accentOf(id: NodeId): NodeAccent = when {
        !isActive -> NodeAccent.NEUTRAL
        id == selected -> NodeAccent.FOCUSED
        id in roles -> NodeAccent.RELATED
        else -> NodeAccent.MUTED
    }

    fun accentOf(edge: LayoutEdge): NodeAccent {
        if (!isActive) return NodeAccent.NEUTRAL
        val (from, to) = edge.endpoints
        return if (isRelated(from) && isRelated(to)) NodeAccent.RELATED else NodeAccent.MUTED
    }

    companion object {
        val NONE: TreeHighlight = TreeHighlight()

        fun focusedOn(id: NodeId?): TreeHighlight = TreeHighlight(selected = id)
    }
}
