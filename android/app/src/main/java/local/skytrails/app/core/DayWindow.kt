package local.skytrails.app.core

import java.time.LocalDate
import java.time.ZoneId

/** Half-open civil day; DST, midnight transitions and skipped dates belong to ZoneRules. */
fun dayWindow(date: LocalDate, zone: ZoneId): DayWindow = DayWindow(
    date.atStartOfDay(zone).toInstant(),
    date.plusDays(1).atStartOfDay(zone).toInstant()
)
