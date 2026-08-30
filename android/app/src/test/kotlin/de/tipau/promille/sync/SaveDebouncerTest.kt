package de.tipau.promille.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SaveDebouncerTest {

    @Test
    fun `rapid sequential writes are coalesced into a single execution`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val debouncer = SaveDebouncer(delayMs = 300L, scope = testScope)

        val executions = AtomicInteger(0)
        var lastValue = ""

        debouncer.schedule {
            executions.incrementAndGet()
            lastValue = "v1"
        }
        testScope.advanceTimeBy(100)

        debouncer.schedule {
            executions.incrementAndGet()
            lastValue = "v2"
        }
        testScope.advanceTimeBy(100)

        debouncer.schedule {
            executions.incrementAndGet()
            lastValue = "v3"
        }

        // Before delay expires, nothing should have run yet
        assertEquals(0, executions.get())

        // Advance past debounce window (300ms)
        testScope.advanceTimeBy(350)
        testScope.advanceUntilIdle()

        assertEquals(1, executions.get(), "N writes in the window must execute exactly once")
        assertEquals("v3", lastValue, "only the newest action must execute")
    }

    @Test
    fun `flush executes pending action immediately without waiting for delay`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val debouncer = SaveDebouncer(delayMs = 5000L, scope = testScope)

        var executed = false
        debouncer.schedule {
            executed = true
        }

        // Immediately flush (e.g. app moving to background)
        debouncer.flush()
        testScope.advanceUntilIdle()

        assertEquals(true, executed, "flush must immediately execute the scheduled save")
    }
}
