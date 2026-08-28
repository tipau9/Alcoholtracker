package de.tipau.promille.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Port of SaveDebouncer.swift.
 * Coalesces rapid sequential save or sync operations within a time window into a single execution,
 * and supports flushing immediately (e.g. on app background).
 */
class SaveDebouncer(
    private val delayMs: Long = 300L,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private var job: Job? = null
    private var pendingAction: (suspend () -> Unit)? = null
    private val mutex = Mutex()

    fun schedule(action: suspend () -> Unit) {
        scope.launch {
            mutex.withLock {
                job?.cancel()
                pendingAction = action
                job = scope.launch {
                    delay(delayMs)
                    val act = mutex.withLock {
                        val a = pendingAction
                        pendingAction = null
                        job = null
                        a
                    }
                    act?.invoke()
                }
            }
        }
    }

    suspend fun flush() {
        val actionToRun = mutex.withLock {
            job?.cancel()
            job = null
            val a = pendingAction
            pendingAction = null
            a
        }
        actionToRun?.invoke()
    }
}
