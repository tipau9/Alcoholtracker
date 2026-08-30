package de.tipau.promille.bac

/**
 * The one destructive decision in HistorySyncService, pulled out of the network
 * code so it can be tested with a JDK alone.
 *
 * The model is a single account backup, not a multi device CRDT: local storage is
 * the source of truth and an ongoing sync deletes server rows that are missing
 * locally, so deleting a drink on the phone actually deletes it. Two exceptions
 * never delete, because both look exactly like "everything was deleted":
 *
 * - a fresh device, where local is empty and the backup is not: restore instead.
 * - the first sync after signing in (merge), where the union is the safe answer.
 */
data class SyncPlan(
    /** Remote ids missing locally that should be pulled down. */
    val toImport: List<String>,
    /** Remote ids missing locally that should be deleted on the server. */
    val toDeleteRemotely: List<String>
)

fun syncPlan(localIds: Set<String>, remoteIds: List<String>, merge: Boolean): SyncPlan {
    val missingLocally = remoteIds.filter { it !in localIds }
    return if (merge || localIds.isEmpty()) SyncPlan(missingLocally, emptyList())
    else SyncPlan(emptyList(), missingLocally)
}
