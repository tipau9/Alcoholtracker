package de.tipau.promille.bac

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlcoholKineticsTest {

    @Test
    fun testZeroOrderPhase() {
        val peak = 0.80
        val beta = 0.15
        // At 1 hour, BAC should be peak - beta * 1 = 0.65 (well above km = 0.10)
        val bac1h = AlcoholKinetics.bacAtTime(peak, 1.0, beta)
        assertEquals(0.65, bac1h, 0.001)

        // At 2 hours: 0.80 - 0.30 = 0.50
        val bac2h = AlcoholKinetics.bacAtTime(peak, 2.0, beta)
        assertEquals(0.50, bac2h, 0.001)
    }

    @Test
    fun testFirstOrderTailPhase() {
        val peak = 0.80
        val beta = 0.15
        // time to km: (0.80 - 0.10) / 0.15 = 4.6667 hours
        val timeToKm = (0.80 - 0.10) / 0.15
        val bacAtKm = AlcoholKinetics.bacAtTime(peak, timeToKm, beta)
        assertEquals(0.10, bacAtKm, 0.001)

        // Beyond km, exponential decay
        val bacLate = AlcoholKinetics.bacAtTime(peak, timeToKm + 1.0, beta)
        assertTrue(bacLate > 0.0)
        assertTrue(bacLate < 0.10)
    }

    @Test
    fun testHoursUntilThreshold() {
        val peak = 0.80
        val beta = 0.15
        // Threshold 0.50 (above km) -> (0.80 - 0.50) / 0.15 = 2.0 hours
        val hours05 = AlcoholKinetics.hoursUntilThreshold(peak, 0.50, beta)
        assertEquals(2.0, hours05, 0.001)

        // Threshold 0.05 (below km) -> timeToKm + ln(0.10 / 0.05) / (0.15 / 0.10)
        val hours005 = AlcoholKinetics.hoursUntilThreshold(peak, 0.05, beta)
        assertTrue(hours005 > (peak - 0.05) / beta)
    }

    @Test
    fun testInvalidInputs() {
        assertEquals(0.0, AlcoholKinetics.bacAtTime(0.0, 1.0, 0.15), 0.0001)
        assertEquals(0.0, AlcoholKinetics.bacAtTime(-1.0, 1.0, 0.15), 0.0001)
        assertEquals(0.0, AlcoholKinetics.bacAtTime(1.0, -1.0, 0.15), 0.0001)
        assertEquals(0.0, AlcoholKinetics.bacAtTime(1.0, 1.0, 0.0), 0.0001)
        assertEquals(0.0, AlcoholKinetics.hoursUntilThreshold(0.5, 0.8, 0.15), 0.0001)
    }
}
