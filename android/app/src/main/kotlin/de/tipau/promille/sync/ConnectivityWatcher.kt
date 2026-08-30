package de.tipau.promille.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Counterpart of the NWPathMonitor in OfflineSyncQueue.swift: when the phone gets
 * a network back, drain the queue. Without this, a queued permille sits there
 * until the user happens to open a screen that syncs.
 */
class ConnectivityWatcher(
    context: Context,
    private val scope: CoroutineScope,
    private val onOnline: suspend () -> Unit
) {

    private val manager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scope.launch { onOnline() }
        }
    }

    fun start() {
        runCatching { manager.registerDefaultNetworkCallback(callback) }
    }

    fun stop() {
        runCatching { manager.unregisterNetworkCallback(callback) }
    }
}
