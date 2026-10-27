package local.skytrails.app

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import local.skytrails.app.location.ManualLocationInput
import local.skytrails.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class FinalDateBoundaryTest {
 @get:Rule val ui=createComposeRule()
 private fun place(zone:String)=ManualLocationInput("边界测试","0","0",zone).selection()

 @Test fun manualInvalidDatesNeverCommitAndCorrectedLeapDayCommitsExactly() {
  var chosen:Instant?=null
  var dismissed=false
  ui.setContent{SkyTheme(AppThemeMode.LIGHT){ManualDateTimeDialog(
   AppState(instant=Instant.parse("2026-01-01T12:00:00Z"),location=place("UTC")),
   {dismissed=true},{chosen=it})}}
  listOf("2026-02-30 19:00","2026-04-31 12:00","2025-02-29 12:00","2026-01-01 24:00").forEach { value->
   ui.onNodeWithTag("datetime_input").performTextReplacement(value)
   ui.onNodeWithTag("datetime_confirm").performClick()
   ui.onNodeWithText("日期或时间无效",substring=true).assertExists()
   ui.runOnIdle{assertNull(chosen);assertFalse(dismissed)}
  }
  ui.onNodeWithTag("datetime_input").performTextReplacement("2024-02-29 19:30")
  ui.onNodeWithTag("datetime_confirm").performClick()
  ui.runOnIdle{assertEquals(Instant.parse("2024-02-29T19:30:00Z"),chosen);assertTrue(dismissed)}
 }

 @Test fun upperTimezoneBoundaryDisablesTimelineAndDateAndNowRecover()=boundary(
  "2051-01-01T09:00:00Z","Pacific/Honolulu","Pacific/Kiritimati","2050-12-31T09:00:00Z")

 @Test fun lowerTimezoneBoundaryDisablesTimelineAndDateAndNowRecover()=boundary(
  "1999-12-31T10:00:00Z","Pacific/Kiritimati","Pacific/Honolulu","2000-01-01T10:00:00Z")

 private fun boundary(at:String,from:String,to:String,recovered:String) {
  val original=Instant.parse(at)
  val now=Instant.parse("2026-10-23T12:00:00Z")
  var s by mutableStateOf(AppState(instant=original,live=false,location=place(from)))
  var showDate by mutableStateOf(false)
  ui.setContent{SkyTheme(AppThemeMode.LIGHT){Column {
   TimeControls(s,{s=s.preview(it)},{s=s.now(now)},{showDate=true})
   if(showDate)DateTimeDialog(s,{showDate=false},{s=s.preview(it)})
  }}}
  ui.onNodeWithTag("time_slider").assertIsEnabled()
  ui.runOnIdle{s=s.select(place(to))}
  ui.onNodeWithTag("time_slider").assertIsNotEnabled().performTouchInput{swipeLeft()}
  ui.onNodeWithTag("timeline_out_of_range").assertExists()
  ui.runOnIdle{assertEquals(original,s.instant)}
  ui.onNodeWithTag("edit_datetime").performClick()
  ui.onNodeWithTag("local_calendar").assertExists()
  ui.runOnIdle{assertEquals(original,s.instant)}
  ui.onNodeWithTag("calendar_next").performClick()
  ui.onNodeWithTag("datetime_confirm").performClick()
  ui.onNodeWithTag("time_slider").assertIsEnabled()
  ui.runOnIdle{assertEquals(Instant.parse(recovered),s.instant);s=s.preview(original)}
  ui.onNodeWithTag("time_slider").assertIsNotEnabled()
  ui.onNodeWithTag("return_now").performClick()
  ui.onNodeWithTag("time_slider").assertIsEnabled()
  ui.runOnIdle{assertEquals(now,s.instant);assertTrue(s.live)}
 }
}
