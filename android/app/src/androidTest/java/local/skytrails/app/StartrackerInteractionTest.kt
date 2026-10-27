package local.skytrails.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import local.skytrails.app.location.ManualLocationInput
import local.skytrails.app.ui.*
import local.skytrails.app.ui.renderer.CameraRuntime
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Dedicated emulator fixtures. No device GPS fix and no physical direction assertions. */
class StartrackerInteractionTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 @Before fun manualClock(){ui.mainClock.autoAdvance=false}
 private fun pump(condition:()->Boolean){ui.waitUntil(15000){ui.mainClock.advanceTimeBy(64);condition()}}
 private fun frame(){ui.mainClock.advanceTimeBy(64);ui.waitForIdle()}
 @Test fun switchesSupportAllOffAndSunMoonTogether() {
  var s by mutableStateOf(AppState())
  ui.setContent{SkyTheme(AppThemeMode.DARK){ObserveScreen(s,null,remember{CameraRuntime()},{s=it(s)},{},{},{},{},{})}}
  frame()
  for(target in SkyTarget.entries){ui.onNodeWithTag("category_${target.name}").performClick();frame();ui.onNodeWithTag("display_layer_${target.name}").assertIsOn().performClick();frame();ui.onNodeWithTag("display_layer_${target.name}").assertIsOff()}
  assertEquals(SkyLayers(false,false,false),s.layers)
  ui.onNodeWithTag("category_SUN").performClick();frame();ui.onNodeWithTag("display_layer_SUN").performClick();frame()
  ui.onNodeWithTag("category_MOON").performClick();frame();ui.onNodeWithTag("display_layer_MOON").performClick();frame()
  assertEquals(SkyLayers(true,true,false),s.layers)
  ui.onNodeWithTag("display_layer_MOON").assertIsOn()
  ui.onNodeWithTag("category_SUN").performClick();frame();ui.onNodeWithTag("display_layer_SUN").assertIsOn()
  ui.onNodeWithTag("category_GALAXY").performClick();frame();ui.onNodeWithTag("display_layer_GALAXY").assertIsOff()
 }
 @Test fun hidingBottomRemovesMasksWithoutRecreatingCameraOrPose() {
  val runtime=CameraRuntime()
  var s by mutableStateOf(AppState(location=ManualLocationInput("isolated onsite fixture","30","120","Asia/Shanghai",confirmedOnSite=true).selection(),cameraPermission=true))
  ui.setContent{SkyTheme(AppThemeMode.DARK){ObserveScreen(s,null,runtime,{s=it(s)},{},{},{},{},{})}}
  pump{runtime.view!=null&&runtime.pose!=null&&runtime.windowMasks.containsKey("observation")}
  val view=runtime.view;val pose=runtime.pose
  ui.onNodeWithTag("hide_controls").performClick();frame()
  ui.onNodeWithTag("observation_controls").assertDoesNotExist();ui.onNodeWithTag("time_slider").assertDoesNotExist();ui.onNodeWithTag("restore_controls").assertIsDisplayed()
  assertFalse(runtime.windowMasks.containsKey("observation"));assertSame(view,runtime.view);assertSame(pose,runtime.pose)
  ui.onNodeWithTag("restore_controls").performClick();frame()
  assertFalse(runtime.windowMasks.containsKey("restore"));assertTrue(runtime.windowMasks.containsKey("observation"));assertSame(view,runtime.view);assertSame(pose,runtime.pose)
 }
 @Test fun immersiveModeHidesNavigationAndRestoresAcrossTabChanges() {
  lateinit var model:SkyViewModel
  ui.runOnUiThread {model=ViewModelProvider(ui.activity)[SkyViewModel::class.java]}
  ui.setContent{SkyApp(model,{},{},{})}
  pump{model.settingsReady.value}
  ui.onNodeWithTag("hide_controls").performClick();frame()
  ui.onNodeWithTag("bottom_navigation").assertDoesNotExist();ui.onNodeWithTag("time_slider").assertDoesNotExist()
  ui.runOnIdle{model.change{it.copy(tab=AppTab.PLAN)}};frame()
  ui.onNodeWithTag("bottom_navigation").assertExists()
  ui.onNodeWithTag("tab_OBSERVE").performClick();frame()
  ui.onNodeWithTag("bottom_navigation").assertDoesNotExist()
  ui.onNodeWithTag("restore_controls").performClick();frame()
  ui.onNodeWithTag("bottom_navigation").assertExists();ui.onNodeWithTag("time_slider").assertExists()
 }
}
