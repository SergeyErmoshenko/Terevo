package me.terevo.ui

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class StoreTest {

    @Test
    fun `intent sequence produces expected state sequence`() = runTest {
        val store = CounterStore(StandardTestDispatcher(testScheduler))

        store.state.test {
            assertEquals(CounterState(0), awaitItem())
            store.dispatch(CounterIntent.Increment)
            assertEquals(CounterState(1), awaitItem())
            store.dispatch(CounterIntent.Add(4))
            assertEquals(CounterState(5), awaitItem())
            store.dispatch(CounterIntent.Reset)
            assertEquals(CounterState(0), awaitItem())
            store.close()
        }
    }

    private data class CounterState(val value: Int)

    private sealed interface CounterIntent {
        data object Increment : CounterIntent
        data class Add(val value: Int) : CounterIntent
        data object Reset : CounterIntent
    }

    private class CounterStore(dispatcher: CoroutineDispatcher) : Store<CounterState, CounterIntent>(CounterState(0), dispatcher) {
        override suspend fun handle(intent: CounterIntent, state: CounterState): CounterState = when (intent) {
            CounterIntent.Increment -> state.copy(value = state.value + 1)
            is CounterIntent.Add -> state.copy(value = state.value + intent.value)
            CounterIntent.Reset -> CounterState(0)
        }
    }
}
