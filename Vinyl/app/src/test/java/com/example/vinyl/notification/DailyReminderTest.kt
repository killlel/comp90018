package com.example.vinyl.notification

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyReminderTest {

    private val today = LocalDate.of(2026, 10, 5)
    private val melbourne = ZoneId.of("Australia/Melbourne")

    @Test
    fun `reminds when wanted, allowed and not yet visited today`() {
        assertTrue(shouldRemind(enabled = true, allowed = true, lastPulled = today.minusDays(1), today = today))
        assertTrue(shouldRemind(enabled = true, allowed = true, lastPulled = null, today = today))
    }

    @Test
    fun `stays quiet once today's music has been picked up`() {
        assertFalse(shouldRemind(enabled = true, allowed = true, lastPulled = today, today = today))
    }

    @Test
    fun `stays quiet when turned off or not allowed`() {
        assertFalse(shouldRemind(enabled = false, allowed = true, lastPulled = null, today = today))
        assertFalse(shouldRemind(enabled = true, allowed = false, lastPulled = null, today = today))
    }

    @Test
    fun `aims for later today when the hour hasn't come yet`() {
        val now = ZonedDateTime.of(2026, 10, 5, 9, 30, 0, 0, melbourne)
        assertEquals(Duration.ofHours(8).plusMinutes(30), delayUntilNext(18, now))
    }

    @Test
    fun `rolls over to tomorrow at or after the hour`() {
        assertEquals(Duration.ofDays(1), delayUntilNext(18, ZonedDateTime.of(2026, 10, 5, 18, 0, 0, 0, melbourne)))
        assertEquals(Duration.ofHours(18), delayUntilNext(18, ZonedDateTime.of(2026, 10, 5, 0, 0, 0, 0, melbourne)))
    }

    @Test
    fun `daylight saving start still lands on the local hour`() {
        // Melbourne skips 2:00 -> 3:00 on 2026-10-04, so that day is 23 hours long.
        val now = ZonedDateTime.of(2026, 10, 3, 20, 0, 0, 0, melbourne)
        val next = now.plus(delayUntilNext(18, now))
        assertEquals(18, next.withZoneSameInstant(melbourne).hour)
        assertEquals(LocalDate.of(2026, 10, 4), next.toLocalDate())
    }

    @Test
    fun `daylight saving end still lands on the local hour`() {
        // Melbourne repeats 2:00 -> 3:00 on 2026-04-05, so that day is 25 hours long.
        val now = ZonedDateTime.of(2026, 4, 4, 20, 0, 0, 0, melbourne)
        val delay = delayUntilNext(18, now)

        assertEquals(Duration.ofHours(23), delay)
        assertEquals(18, now.plus(delay).withZoneSameInstant(melbourne).hour)
    }

    @Test
    fun `one second before the hour waits one second`() {
        val now = ZonedDateTime.of(2026, 10, 5, 17, 59, 59, 0, melbourne)
        assertEquals(Duration.ofSeconds(1), delayUntilNext(18, now))
    }

    @Test
    fun `just past the hour waits almost a full day`() {
        val now = ZonedDateTime.of(2026, 10, 5, 18, 0, 1, 0, melbourne)
        assertEquals(Duration.ofDays(1).minusSeconds(1), delayUntilNext(18, now))
    }

    @Test
    fun `the delay is never zero or negative`() {
        for (hour in 0..23) {
            for (minute in listOf(0, 30, 59)) {
                val now = ZonedDateTime.of(2026, 10, 5, hour, minute, 0, 0, melbourne)
                val delay = delayUntilNext(18, now)
                assertTrue("at $hour:$minute", !delay.isNegative && !delay.isZero)
                assertTrue("at $hour:$minute", delay <= Duration.ofDays(1))
            }
        }
    }

    @Test
    fun `aims at the local hour in whatever zone the phone is in`() {
        val now = ZonedDateTime.of(2026, 10, 5, 12, 0, 0, 0, ZoneId.of("Europe/London"))
        assertEquals(Duration.ofHours(6), delayUntilNext(18, now))
    }

    @Test
    fun `crossing midnight into a new year still finds tomorrow`() {
        val now = ZonedDateTime.of(2026, 12, 31, 23, 0, 0, 0, melbourne)
        val next = now.plus(delayUntilNext(18, now))
        assertEquals(LocalDate.of(2027, 1, 1), next.toLocalDate())
    }

    @Test
    fun `a pull on an earlier day doesn't silence today`() {
        assertTrue(shouldRemind(enabled = true, allowed = true, lastPulled = today.minusDays(30), today = today))
    }

    @Test
    fun `turned off and not allowed together still stays quiet, even if never pulled`() {
        assertFalse(shouldRemind(enabled = false, allowed = false, lastPulled = null, today = today))
        assertFalse(shouldRemind(enabled = false, allowed = false, lastPulled = today, today = today))
    }
}
