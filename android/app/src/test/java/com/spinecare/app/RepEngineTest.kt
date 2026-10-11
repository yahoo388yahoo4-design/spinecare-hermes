package com.spinecare.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for the rep engine — guards against the counting bugs found
 *  during video testing (first excursion counted as setup, off-by-one totals). */
class RepEngineTest {
    @Test
    fun countsTurnAndReturnCycles() {
        val e = RepEngine(low = 8.0, high = 35.0, minHoldMs = 400)
        var t = 0L
        var counted = 0
        // 5 full cycles: rest -> above high -> below low (rep completes on return)
        repeat(5) {
            t += 1000; e.update(40.0, t)
            t += 1000; e.update(40.0, t)
            t += 1000; assertTrue("rep should complete on return", e.update(2.0, t))
            t += 1000; e.update(2.0, t)
        }
        assertEquals(5, e.reps)
    }

    @Test
    fun ignoresShortExcursions() {
        val e = RepEngine(low = 8.0, high = 35.0, minHoldMs = 400)
        e.update(40.0, 0)
        e.update(2.0, 100)   // only 100ms above high -> below minHold
        assertEquals(0, e.reps)
    }

    @Test
    fun ignoresNullAndNaN() {
        val e = RepEngine(low = 0.0, high = 1.0)
        assertFalse(e.update(null, 0))
        assertFalse(e.update(Double.NaN, 100))
        assertEquals(0, e.reps)
    }

    @Test
    fun signalValueHandlesAbsPrefix() {
        val m = mapOf("neck_yaw_est" to -25.0, "trunk_tilt_frontal" to 3.0)
        assertEquals(25.0, signalValue(m, "abs:neck_yaw_est"))
        assertEquals(3.0, signalValue(m, "trunk_tilt_frontal"))
        assertEquals(null, signalValue(m, "missing_metric"))
    }
}
