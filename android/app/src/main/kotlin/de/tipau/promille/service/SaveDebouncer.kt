package de.tipau.promille.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debounces database saves mirroring Alcoholtracker/Services/SaveDebouncer.swift 1:1.
 */
class SaveDebouncer(
    private val delayMs: Long = 300L
) {
    private var job: Job? = null

    fun schedule(scope: CoroutineScope, action: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch {
            delay(delayMs)
            action()
        }
    }

    fun flush(scope: CoroutineScope, action: suspend () -> Unit) {
        job?.cancel()
        job = null
        scope.launch { action() }
    }
}

