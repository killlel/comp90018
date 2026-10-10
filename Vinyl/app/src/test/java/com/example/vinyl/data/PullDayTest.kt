package com.example.vinyl.data

import com.example.vinyl.repository.PullStatus
import com.example.vinyl.repository.isDailyPullLimit
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullDayTest {

    @Test
    fun `before 6am it is still yesterday's pull`() {
        assertEquals(LocalDate.of(2026, 10, 9), pullDay(LocalDateTime.of(2026, 10, 10, 5, 59)))
        assertEquals(LocalDate.of(2026, 10, 9), pullDay(LocalDateTime.of(2026, 10, 10, 0, 30)))
    }

    @Test
    fun `from 6am it is today's`() {
        assertEquals(LocalDate.of(2026, 10, 10), pullDay(LocalDateTime.of(2026, 10, 10, 6, 0)))
        assertEquals(LocalDate.of(2026, 10, 10), pullDay(LocalDateTime.of(2026, 10, 10, 23, 59)))
    }

    @Test
    fun `a status with nothing left means no pull`() {
        assertTrue(PullStatus(dealtToday = 0, remaining = 3).canPull)
        assertFalse(PullStatus(dealtToday = 3, remaining = 0).canPull)
    }

    @Test
    fun `the server's limit error is recognised, other failures are not`() {
        assertTrue(RuntimeException("daily pull limit reached").isDailyPullLimit())
        assertFalse(RuntimeException("not authenticated").isDailyPullLimit())
        assertFalse(RuntimeException().isDailyPullLimit())
    }
}
