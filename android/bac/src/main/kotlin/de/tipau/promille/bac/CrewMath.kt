package de.tipau.promille.bac

import kotlin.math.max

/**
 * The friend-list numbers, mirrored from CrewMember.swift.
 *
 * A friend publishes a permille and then closes the app. Showing that value
 * unchanged would leave them pinned at 1.2 all night, so the list decays it at
 * the standard rate since the last server update instead. The rate is the flat
 * default on purpose: a friend's body data is not ours to know.
 */
object CrewMath {

    const val FRIEND_ELIMINATION_RATE = 0.15

    /** Minutes since the friend's last published value, null when none ever arrived. */
    fun updatedMinutesAgo(lastUpdateEpochSeconds: Long?, nowEpochSeconds: Long): Int? =
        lastUpdateEpochSeconds?.let { ((nowEpochSeconds - it) / 60).toInt() }

    fun estimatedBac(
        currentBac: Double,
        lastUpdateEpochSeconds: Long?,
        nowEpochSeconds: Long
    ): Double {
        val ts = lastUpdateEpochSeconds ?: return currentBac
        val hours = max(0.0, (nowEpochSeconds - ts) / 3600.0)
        return max(0.0, currentBac - FRIEND_ELIMINATION_RATE * hours)
    }

    /** 0 sober, 1 tipsy, 2 drunk, 3 careful, 4 danger. Same bands as BACStatus. */
    fun statusLevel(bac: Double): Int = when {
        bac < 0.01 -> 0
        bac < 0.30 -> 1
        bac < 0.80 -> 2
        bac < 1.50 -> 3
        else -> 4
    }

    /**
     * Drives the "braucht Aufmerksamkeit" list on Home and Crew: the band times
     * 20, plus 10 when a high value arrived in the last 10 minutes, which is the
     * difference between a friend who is drunk and one who is drunk right now.
     * The list shows everyone at 40 and above.
     */
    fun careScore(
        currentBac: Double,
        lastUpdateEpochSeconds: Long?,
        nowEpochSeconds: Long
    ): Int {
        val estimated = estimatedBac(currentBac, lastUpdateEpochSeconds, nowEpochSeconds)
        var score = statusLevel(estimated) * 20
        val minutes = updatedMinutesAgo(lastUpdateEpochSeconds, nowEpochSeconds)
        if (minutes != null && minutes < 10 && estimated > 1.0) score += 10
        return score
    }
}
