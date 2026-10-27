package local.skytrails.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import local.skytrails.app.location.ManualLocationInput
import local.skytrails.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class DateTimeDialogTest {
 @get:Rule val ui=createComposeRule()
 private fun state(at:String)=AppState(instant=Instant.parse(at),location=ManualLocationInput("DST","40","-74","America/New_York").selection())
 @Test fun overlapMinuteEditInvalidatesChoiceThenCommitsVisibleMinute() {
  var chosen:Instant?=null
  ui.setContent{SkyTheme(AppThemeMode.LIGHT){DateTimeDialog(state("2026-11-01T05:30:00Z"),{},{chosen=it})}}
  ui.onNodeWithTag("calendar_next").performClick()
  ui.onNodeWithTag("datetime_confirm").performClick()
  ui.onNodeWithTag("dst_-04:00").assertExists()
  ui.onNodeWithTag("time_input_mode").performScrollTo().performClick()
  ui.onAllNodes(hasSetTextAction())[1].performTextReplacement("45")
  ui.onNodeWithTag("dst_-04:00").assertDoesNotExist()
  ui.onNodeWithTag("datetime_confirm").performClick()
  ui.onNodeWithTag("dst_-04:00").performScrollTo().performClick()
  ui.runOnIdle{assertEquals(Instant.parse("2026-11-01T05:45:00Z"),chosen)}
 }
 @Test fun gapMinuteEditAndReturnToCalendarBothInvalidatePendingChoice() {
  var chosen:Instant?=null
  ui.setContent{SkyTheme(AppThemeMode.LIGHT){DateTimeDialog(state("2026-03-08T06:30:00Z"),{},{chosen=it})}}
  ui.onNodeWithTag("calendar_next").performClick()
  ui.onNodeWithTag("time_input_mode").performScrollTo().performClick()
  ui.onAllNodes(hasSetTextAction())[0].performTextReplacement("02")
  ui.onNodeWithTag("datetime_confirm").performClick()
  ui.onNodeWithTag("dst_gap").assertExists()
  ui.onAllNodes(hasSetTextAction())[1].performTextReplacement("45")
  ui.onNodeWithTag("dst_gap").assertDoesNotExist()
  ui.onNodeWithTag("datetime_confirm").performClick()
  ui.onNodeWithTag("calendar_back").performClick()
  ui.onNodeWithTag("calendar_next").performClick()
  ui.onNodeWithTag("dst_gap").assertDoesNotExist()
  ui.runOnIdle{assertNull(chosen)}
  ui.onNodeWithTag("datetime_confirm").performClick()
  ui.onNodeWithTag("dst_gap").performScrollTo().performClick()
  ui.runOnIdle{assertEquals(Instant.parse("2026-03-08T07:00:00Z"),chosen)}
 }
}
