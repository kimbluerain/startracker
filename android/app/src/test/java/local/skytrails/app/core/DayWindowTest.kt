package local.skytrails.app.core

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class DayWindowTest {
    @Test fun londonSpringDayIs23Hours() = checkWindow("2026-03-29", "Europe/London", "2026-03-29T00:00:00Z", "2026-03-29T23:00:00Z", 23)
    @Test fun londonAutumnDayIs25Hours() = checkWindow("2026-10-25", "Europe/London", "2026-10-24T23:00:00Z", "2026-10-26T00:00:00Z", 25)
    @Test fun leapDayEndsAtMarchFirst() = checkWindow("2000-02-29", "Asia/Shanghai", "2000-02-28T16:00:00Z", "2000-02-29T16:00:00Z", 24)
    @Test fun yearBoundaryOnDateLine() = checkWindow("2049-12-31", "Pacific/Kiritimati", "2049-12-30T10:00:00Z", "2049-12-31T10:00:00Z", 24)
    @Test fun skippedCivilDayHasEmptyWindow() = checkWindow("2011-12-30", "Pacific/Apia", "2011-12-30T10:00:00Z", "2011-12-30T10:00:00Z", 0)
    private fun checkWindow(date: String, zone: String, start: String, end: String, hours: Long) {
        val result = runCatching { dayWindow(LocalDate.parse(date), ZoneId.of(zone)) }
        assertTrue("Local-day window must be implemented: ${result.exceptionOrNull()}", result.isSuccess)
        val w = result.getOrThrow()
        assertEquals(Instant.parse(start), w.start)
        assertEquals(Instant.parse(end), w.endExclusive)
        assertEquals(hours, Duration.between(w.start, w.endExclusive).toHours())
    }
}
