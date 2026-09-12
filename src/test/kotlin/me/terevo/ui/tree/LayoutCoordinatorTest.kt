package me.terevo.ui.tree

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.terevo.layout.LayoutRequest
import me.terevo.layout.NodeMetrics
import me.terevo.layout.Size
import me.terevo.layout.WalkerLayoutEngine
import me.terevo.testing.NODE_HEIGHT
import me.terevo.testing.NODE_WIDTH
import me.terevo.testing.graphOf
import me.terevo.testing.nodeId
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LayoutCoordinatorTest {

    @Test
    fun `publishes layout on state flow`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = LayoutCoordinator(this, WalkerLayoutEngine(), dispatcher)
        val request = LayoutRequest(
            graph = graphOf(listOf("person")),
            metrics = NodeMetrics(emptyMap(), Size(NODE_WIDTH, NODE_HEIGHT)),
        )

        coordinator.submit(request)
        advanceUntilIdle()

        assertEquals(setOf(nodeId("person")), coordinator.layout.value.nodes.keys)
    }

    @Test
    fun `latest submitted request replaces pending calculation`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = LayoutCoordinator(this, WalkerLayoutEngine(), dispatcher)
        val metrics = NodeMetrics(emptyMap(), Size(NODE_WIDTH, NODE_HEIGHT))

        coordinator.submit(LayoutRequest(graphOf(listOf("old")), metrics))
        coordinator.submit(LayoutRequest(graphOf(listOf("new")), metrics))
        advanceUntilIdle()

        assertEquals(setOf(nodeId("new")), coordinator.layout.value.nodes.keys)
    }
}
