package local.skytrails.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import local.skytrails.app.location.ManualLocationInput
import local.skytrails.app.ui.*
import local.skytrails.app.ui.renderer.CameraRuntime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Compiles against v2's public interface and exposes its lost-pose transition. */
class StartrackerV3RegressionTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 @Test fun cameraToVirtualRetainsTheSamePoseAndReleasesOnlyCamera() {
  ui.mainClock.autoAdvance=false
  val runtime=CameraRuntime()
  var s by mutableStateOf(AppState(location=ManualLocationInput("synthetic onsite","30","120","Asia/Shanghai",confirmedOnSite=true).selection(),cameraPermission=true))
  ui.setContent{SkyTheme(AppThemeMode.DARK){ObserveScreen(s,null,runtime,{s=it(s)},{},{},{},{},{})}}
  ui.waitUntil(10000){ui.mainClock.advanceTimeBy(64);runtime.pose!=null&&runtime.view!=null}
  ui.onAllNodesWithTag("mode_toggle").assertCountEquals(1)
  ui.onNodeWithTag("request_camera").assertDoesNotExist();ui.onNodeWithTag("virtual_sky").assertDoesNotExist()
  ui.onNodeWithText("实景 AR").assertDoesNotExist();ui.onNodeWithText("星空").assertDoesNotExist()
  val pose=runtime.pose
  ui.onNodeWithTag("mode_toggle").assertContentDescriptionEquals("切换到星空").performClick();ui.mainClock.advanceTimeBy(128);ui.waitForIdle()
  assertNull("Virtual sky must release camera resources",runtime.view)
  assertSame("Virtual sky must retain the active physical orientation provider",pose,runtime.pose)
  ui.onNodeWithTag("mode_toggle").assertContentDescriptionEquals("切换到实景 AR").performClick();ui.mainClock.advanceTimeBy(128);ui.waitForIdle()
  assertSame("Returning to camera must use the same provider",pose,runtime.pose)
  assertNotNull(runtime.view)
 }
}
