package local.skytrails.app
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModelProvider
import local.skytrails.app.ui.SkyViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SkyNavigationTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 @Before fun requireDeniedFixture() {
  // The emulator-only runner revokes before launching instrumentation (revocation kills the app).
  listOf(Manifest.permission.CAMERA,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION).forEach {
   assertEquals("Run scripts/run-task5-ui-regressions.py with a dedicated emulator",PackageManager.PERMISSION_DENIED,ContextCompat.checkSelfPermission(ui.activity,it))
  }
 }
 @Test fun deniedPermissionsStillAllowManualEventsPreviewAndNow() {
  ui.onNodeWithTag("tab_PLAN").performClick();ui.onNodeWithTag("plan_location").assertExists()
  ui.onNodeWithTag("tab_PLACES").performClick()
  ui.onNodeWithTag("manual_open").performScrollTo().performClick()
  ui.onNodeWithTag("manual_name").performScrollTo().performTextInput("离线测试地点")
  ui.onNodeWithTag("manual_latitude").performScrollTo().performTextInput("30.25")
  ui.onNodeWithTag("manual_longitude").performScrollTo().performTextInput("120.16")
  ui.onNodeWithTag("manual_zone").performScrollTo().performTextInput("Asia/Shanghai")
  ui.onNodeWithTag("manual_save").performClick()
  ui.onNodeWithTag("tab_PLAN").performClick()
  ui.waitUntil(15000){ui.onAllNodesWithTag("event_0").fetchSemanticsNodes().isNotEmpty()}
  ui.onNodeWithTag("event_0").performScrollTo().performClick()
  ui.onNodeWithTag("preview_label").performScrollTo().assertExists()
  ui.onNodeWithTag("return_now").performScrollTo().performClick()
  ui.onAllNodesWithTag("preview_label").assertCountEquals(0)
  lateinit var model:SkyViewModel
  ui.runOnIdle {model=ViewModelProvider(ui.activity)[SkyViewModel::class.java]}
  val beforeDrag=model.state.value.instant
  // Stay inside the hit region: edge x=0 can be clipped by the scrolling parent.
  ui.onNodeWithTag("time_slider").performScrollTo().assertIsDisplayed().performTouchInput {
   swipe(Offset(width*.2f,centerY),Offset(width*.8f,centerY),durationMillis=400)
  }
  ui.runOnIdle {assertFalse("A real drag must stop live time",model.state.value.live);assertNotEquals(beforeDrag,model.state.value.instant)}
  ui.onNodeWithTag("preview_label").performScrollTo().assertExists()
  ui.onNodeWithTag("return_now").performScrollTo().performClick()
  ui.onAllNodesWithTag("preview_label").assertCountEquals(0)
  ui.onNodeWithTag("tab_OBSERVE").performClick()
  ui.onNodeWithText("计算位置 · 虚拟天空").assertExists()
 }
}
