package com.example.vinyl.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeAlgorithmTest {

    // z carries all the acceleration for these tests; x/y stay at rest (0).
    private fun reading(algo: ShakeAlgorithm, gForce: Float, atMs: Long) =
        algo.onReading(0f, 0f, gForce * EARTH_GRAVITY, atMs)

    private fun shakeAt(algo: ShakeAlgorithm, vararg timesMs: Long, gForce: Float = 3.5f): List<Boolean> =
        timesMs.map { reading(algo, gForce, it) }

    @Test
    fun `a gentle motion never triggers`() {
        val algo = ShakeAlgorithm()
        // below the 2.7g threshold, even a lot of them, spread out enough to count as separate peaks
        val results = (0 until 20).map { reading(algo, gForce = 1.2f, atMs = it * 200L) }
        assertTrue(results.none { it })
    }

    @Test
    fun `a single hard jolt does not trigger - it takes more than one`() {
        val algo = ShakeAlgorithm()
        assertFalse(reading(algo, gForce = 4f, atMs = 0))
    }

    @Test
    fun `three jolts close together trigger on the third`() {
        val algo = ShakeAlgorithm(requiredPeakCount = 3, minPeakIntervalMs = 100)
        val results = shakeAt(algo, 0, 150, 300)
        assertEquals(listOf(false, false, true), results)
    }

    @Test
    fun `readings within the same jolt do not count twice`() {
        // a swing of the arm crosses the threshold on several consecutive samples ~16ms apart
        // (60Hz); minPeakIntervalMs filters that down to one peak per swing.
        val algo = ShakeAlgorithm(requiredPeakCount = 3, minPeakIntervalMs = 100)
        val results = shakeAt(algo, 0, 16, 32, 48, 150, 166, 300)
        assertEquals(1, results.count { it })
        assertTrue(results.last())
    }

    @Test
    fun `jolts spread out past the window do not add up to a shake`() {
        val algo = ShakeAlgorithm(requiredPeakCount = 3, peakWindowMs = 1000, minPeakIntervalMs = 100)
        // three jolts, but each pair more than a second apart: never a real shake, e.g. carried
        // in a bag that occasionally bumps something
        val results = shakeAt(algo, 0, 1200, 2400)
        assertTrue("a slow drip of jolts must never trigger", results.none { it })
    }

    @Test
    fun `once triggered, further jolts are ignored during the cooldown`() {
        val algo = ShakeAlgorithm(requiredPeakCount = 2, minPeakIntervalMs = 50, cooldownMs = 1500)
        val first = shakeAt(algo, 0, 100)              // triggers at 100
        assertEquals(listOf(false, true), first)

        val duringCooldown = shakeAt(algo, 200, 400, 600, 800, 1000, 1200, 1400)
        assertTrue("nothing should fire again inside the cooldown", duringCooldown.none { it })
    }

    @Test
    fun `a new shake can fire again once the cooldown has passed`() {
        val algo = ShakeAlgorithm(requiredPeakCount = 2, minPeakIntervalMs = 50, cooldownMs = 1500)
        shakeAt(algo, 0, 100)                            // first shake, triggers at 100
        val second = shakeAt(algo, 1700, 1800)            // well past the 1500ms cooldown
        assertEquals(listOf(false, true), second)
    }

    @Test
    fun `continuous shaking fires repeatedly, once per cooldown window`() {
        val algo = ShakeAlgorithm(requiredPeakCount = 2, minPeakIntervalMs = 100, cooldownMs = 1000)
        // jolts every 150ms for 4 seconds - a sustained shake
        val times = (0 until 27).map { it * 150L }
        val results = times.map { reading(algo, gForce = 3.5f, atMs = it) }
        val fireTimes = times.zip(results).filter { it.second }.map { it.first }

        assertTrue("a sustained shake should fire more than once", fireTimes.size >= 2)
        val gaps = fireTimes.zipWithNext { a, b -> b - a }
        assertTrue("triggers must be spaced at least a cooldown apart: $gaps", gaps.all { it >= 1000 })
    }

    @Test
    fun `default thresholds reject a phone in a pocket while walking`() {
        // typical walking jostle is well under 2.7g and fairly regular
        val algo = ShakeAlgorithm()
        val results = (0 until 60).map { reading(algo, gForce = 1.5f, atMs = it * 500L) }
        assertTrue(results.none { it })
    }

    @Test
    fun `a real shake with the default tuning triggers`() {
        val algo = ShakeAlgorithm()
        // 5 jolts about 150ms apart - roughly a 3-4 Hz hand shake
        val results = shakeAt(algo, 0, 150, 300, 450, 600, gForce = 3.2f)
        assertTrue(results.any { it })
    }
}