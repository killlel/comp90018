package com.example.vinyl.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * One pull of three music cards a day, and the day turns over at this hour on the phone's clock —
 * not at midnight. The server enforces the same rule (migration 0016); this copy only decides what
 * the app shows, so the two must agree.
 */
const val PULL_DAY_STARTS_AT_HOUR = 6

/** The day whose cards [now] belongs to: until 06:00 it is still yesterday's. */
fun pullDay(now: LocalDateTime = LocalDateTime.now()): LocalDate =
    now.minusHours(PULL_DAY_STARTS_AT_HOUR.toLong()).toLocalDate()

/** The phone's time zone, which the server needs to know when 06:00 is for this user. */
fun deviceTimeZone(): String = ZoneId.systemDefault().id
