package me.terevo.ui

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

abstract class Store<S, I>(
    initial: S,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val intents = Channel<I>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(initial)

    val state: StateFlow<S> = mutableState.asStateFlow()

    init {
        scope.launch {
            for (intent in intents) {
                mutableState.value = handle(intent, mutableState.value)
            }
        }
    }

    fun dispatch(intent: I) {
        intents.trySend(intent)
    }

    override fun close() {
        intents.close()
        scope.cancel()
    }

    protected abstract suspend fun handle(intent: I, state: S): S
}
