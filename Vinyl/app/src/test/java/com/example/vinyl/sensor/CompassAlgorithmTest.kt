package com.example.vinyl.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CompassAlgorithmTest {

    /** Shortest signed angular distance from a to b, in degrees - for comparing circular values. */
    private fun angleDiff(a: Float, b: Float): Float {
        var d = (b - a) % 360
        if (d > 180) d -= 360
        if (d < -180) d += 360
        return d
    }

    @Test
    fun `first reading snaps straight to its value, no start-up lag`() {
        val algo = CompassAlgorithm()
        assertEquals(90f, algo.onReading(90f), 0.01f)
    }

    @Test
    fun `a steady heading stays steady`() {
        val algo = CompassAlgorithm()
        repeat(20) { algo.onReading(45f) }
        assertEquals(45f, algo.onReading(45f), 0.01f)
    }

    @Test
    fun `noisy jitter around one heading averages back toward the centre`() {
        val algo = CompassAlgorithm(smoothing = 0.2f)
        val noisy = listOf(100f, 95f, 105f, 98f, 102f, 97f, 103f, 99f, 101f, 100f)
        var last = 0f
        noisy.forEach { last = algo.onReading(it) }
        // Not exactly 100 (it's a rolling average, always a little behind), but close, and
        // nowhere near the noise's full +/-5 degree swing.
        assertEquals(100f, last, 3f)
    }

    @Test
    fun `crossing the 0 360 boundary does not swing to the opposite side`() {
        // This is the case a naive arithmetic mean gets catastrophically wrong: mean(359, 1) = 180.
        val algo = CompassAlgorithm(smoothing = 0.5f)
        algo.onReading(359f)
        val result = algo.onReading(1f)
        // The true answer is ~0 (the short way across the boundary), not 180 (the long way).
        assertTrue("expected near 0, got $result", abs(angleDiff(0f, result)) < 10f)
    }

    @Test
    fun `settles near due north from readings jittering across the 360 0 seam`() {
        val algo = CompassAlgorithm(smoothing = 0.2f)
        val noisy = listOf(358f, 2f, 359f, 1f, 357f, 3f, 0f, 358f, 2f, 0f)
        var last = 0f
        noisy.forEach { last = algo.onReading(it) }
        assertTrue("expected near 0/360, got $last", abs(angleDiff(0f, last)) < 10f)
    }

    @Test
    fun `lower smoothing factor reacts to a jump more slowly than a higher one`() {
        val slow = CompassAlgorithm(smoothing = 0.05f)
        val fast = CompassAlgorithm(smoothing = 0.5f)
        slow.onReading(0f); fast.onReading(0f)
        val slowResult = slow.onReading(90f)
        val fastResult = fast.onReading(90f)
        assertTrue("fast filter should have moved further toward 90 than the slow one",
            abs(angleDiff(0f, fastResult)) > abs(angleDiff(0f, slowResult)))
    }

    // ---------------------------------------------------- arrow rotation combination

    @Test
    fun `facing the target bearing head-on points the arrow straight up (0)`() {
        assertEquals(0f, CompassAlgorithm.arrowRotationDegrees(deviceHeading = 90f, targetBearing = 90f), 0.01f)
    }

    @Test
    fun `target is 90 degrees clockwise of where the phone faces`() {
        assertEquals(90f, CompassAlgorithm.arrowRotationDegrees(deviceHeading = 0f, targetBearing = 90f), 0.01f)
    }

    @Test
    fun `target behind the phone wraps to 180, not negative`() {
        assertEquals(180f, CompassAlgorithm.arrowRotationDegrees(deviceHeading = 0f, targetBearing = 180f), 0.01f)
    }

    @Test
    fun `a negative raw difference wraps into 0 until 360`() {
        val r = CompassAlgorithm.arrowRotationDegrees(deviceHeading = 350f, targetBearing = 10f)
        assertEquals(20f, r, 0.01f) // 10 - 350 = -340, wraps to 20
        assertTrue(r in 0f..360f)
    }

    @Test
    fun `rotating the phone a full circle brings the arrow back to where it started`() {
        val target = 40f
        val before = CompassAlgorithm.arrowRotationDegrees(deviceHeading = 10f, targetBearing = target)
        val after = CompassAlgorithm.arrowRotationDegrees(deviceHeading = 10f + 360f, targetBearing = target)
        assertEquals(before, after, 0.01f)
    }
}