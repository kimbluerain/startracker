package local.skytrails.app.ui

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DateTimeChoiceTest {
 private val zone=ZoneId.of("America/New_York")
 @Test fun editingOverlapMinuteRejectsOldOffsetChoiceAndResolvesVisibleTime() {
  val before=DateTimeInput(LocalDateTime.parse("2026-11-01T01:30"),zone)
  val after=before.copy(local=before.local.plusMinutes(15))
  val old=PendingDateTime(before)
  assertNull(old.choose(after,ZoneOffset.ofHours(-4)))
  assertEquals(Instant.parse("2026-11-01T05:45:00Z"),PendingDateTime(after).choose(after,ZoneOffset.ofHours(-4)))
 }
 @Test fun gapAndCalendarOrZoneEditsRejectPreviousResolution() {
  val gap=DateTimeInput(LocalDateTime.parse("2026-03-08T02:30"),zone)
  val old=PendingDateTime(gap)
  assertEquals(Instant.parse("2026-03-08T07:00:00Z"),old.choose(gap))
  assertNull(old.choose(gap.copy(local=gap.local.plusMinutes(15))))
  assertNull(old.choose(gap.copy(local=gap.local.plusDays(1))))
  assertNull(old.choose(gap.copy(zone=ZoneId.of("UTC"))))
  val next=gap.copy(local=gap.local.plusDays(1))
  assertEquals(Instant.parse("2026-03-09T06:30:00Z"),PendingDateTime(next).choose(next))
 }

 @Test fun invalidCalendarDatesAndMidnightOverflowAreRejectedWithoutNormalization() {
  listOf("2026-02-30 19:00","2026-04-31 12:00","2025-02-29 12:00","2026-13-01 12:00","2026-01-01 24:00").forEach { value->
   assertTrue("Must reject $value",runCatching{parseManualDateTime(value)}.isFailure)
  }
 }
 @Test fun leapDayAndSupportedEndpointsRemainExact() {
  assertEquals(LocalDateTime.of(2024,2,29,19,30),parseManualDateTime(" 2024-02-29 19:30 "))
  assertEquals(LocalDateTime.of(2000,1,1,0,0),parseManualDateTime("2000-01-01 00:00"))
  assertEquals(LocalDateTime.of(2050,12,31,23,59),parseManualDateTime("2050-12-31 23:59"))
  listOf("1999-12-31 23:59","2051-01-01 00:00").forEach{assertTrue(runCatching{parseManualDateTime(it)}.isFailure)}
 }
 @Test fun manualDstInputsStillRequireGapOrOverlapResolution() {
  val gap=DateTimeInput(parseManualDateTime("2026-03-08 02:30"),zone)
  assertEquals(Instant.parse("2026-03-08T07:00:00Z"),PendingDateTime(gap).choose(gap))
  val overlap=DateTimeInput(parseManualDateTime("2026-11-01 01:30"),zone)
  assertNull(PendingDateTime(overlap).choose(overlap))
  assertEquals(Instant.parse("2026-11-01T06:30:00Z"),PendingDateTime(overlap).choose(overlap,ZoneOffset.ofHours(-5)))
 }
 @Test fun upperTimezoneBoundaryPreservesInstantAndDeclinesTimelinePreview() {
  val at=Instant.parse("2051-01-01T09:00:00Z") // Dec 31 23:00 in Honolulu.
  val place=local.skytrails.app.location.ManualLocationInput("东","0","0","Pacific/Kiritimati").selection()
  val s=AppState(instant=at,live=false).select(place)
  assertEquals(at,s.instant)
  assertEquals(LocalDate.of(2051,1,1),s.instant.atZone(place.site.zone).toLocalDate())
  assertNull(timelinePreviewInstant(s.instant,place.site.zone,0.5))
 }
 @Test fun lowerTimezoneBoundaryPreservesInstantAndDeclinesTimelinePreview() {
  val at=Instant.parse("1999-12-31T10:00:00Z") // Jan 1 00:00 in Kiritimati.
  val place=local.skytrails.app.location.ManualLocationInput("西","0","0","Pacific/Honolulu").selection()
  val s=AppState(instant=at,live=false).select(place)
  assertEquals(at,s.instant)
  assertEquals(LocalDate.of(1999,12,31),s.instant.atZone(place.site.zone).toLocalDate())
  assertNull(timelinePreviewInstant(s.instant,place.site.zone,0.5))
 }
 @Test fun supportedTimelineRetainsDstDayDuration() {
  assertEquals(Instant.parse("2026-03-08T16:30:00Z"),timelinePreviewInstant(Instant.parse("2026-03-08T12:00:00Z"),zone,0.5))
 }
}
