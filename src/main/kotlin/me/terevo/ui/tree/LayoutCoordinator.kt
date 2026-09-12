package me.terevo.ui.tree

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.terevo.layout.Layout
import me.terevo.layout.LayoutEngine
import me.terevo.layout.LayoutRequest

class LayoutCoordinator(
    private val scope: CoroutineScope,
    private val engine: LayoutEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutableLayout = MutableStateFlow(Layout.EMPTY)
    private var calculation: Job? = null

    val layout: StateFlow<Layout> = mutableLayout.asStateFlow()

    fun submit(request: LayoutRequest) {
        calculation?.cancel()
        calculation = scope.launch(dispatcher) {
            mutableLayout.value = engine.layout(request)
        }
    }
}
