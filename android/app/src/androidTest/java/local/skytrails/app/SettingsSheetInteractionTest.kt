package local.skytrails.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import local.skytrails.app.ui.*
import local.skytrails.app.ui.renderer.CameraRuntime
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsSheetInteractionTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 @Before fun clock(){ui.mainClock.autoAdvance=false}
 private fun frame(){ui.mainClock.advanceTimeBy(64);ui.waitForIdle()}
 @Test fun settingsIsSameWindowBlocksUnderlayAndRemovesItsBlurMaskOnClose() {
  val runtime=CameraRuntime();var open by mutableStateOf(true);var taps=0
  ui.setContent{SkyTheme(AppThemeMode.DARK){CompositionLocalProvider(LocalFrostedBackdrop provides remember{FrostedBackdrop()}){
   Box(Modifier.fillMaxSize()){Button({taps++},Modifier.align(Alignment.Center)){Text("underlay")};if(open)SettingsDialog(AppState(theme=AppThemeMode.DARK),{},runtime){open=false}}
  }}}
  frame();assertTrue(runtime.windowMasks.containsKey("settings_sheet"));ui.onAllNodes(isDialog()).assertCountEquals(0)
  ui.onRoot().performTouchInput{click(center)};frame();assertEquals(0,taps)
  ui.onNodeWithTag("settings_close").performClick();frame()
  assertFalse(open);assertFalse(runtime.windowMasks.containsKey("settings_sheet"))
 }
 @Test fun openingModalRemovesUnderlyingHudAndNavigationMasksWithoutRestartingSkyOrPose() {
  val runtime=CameraRuntime();var state by mutableStateOf(AppState(theme=AppThemeMode.DARK));var open by mutableStateOf(false)
  ui.setContent{SkyTheme(state.theme){Box(Modifier.fillMaxSize()){
   ObserveScreen(state,null,runtime,{state=it(state)},{},{},{},{open=true},{},hudVisible=!open)
   if(open)SettingsDialog(state,{state=it(state)},runtime){open=false}
  }}}
  frame();val pose=runtime.pose;val sky=runtime.skyView
  ui.onNodeWithTag("settings").performClick();frame()
  ui.onNodeWithTag("observe_header").assertDoesNotExist();ui.onNodeWithTag("observation_controls").assertDoesNotExist();ui.onNodeWithTag("time_slider").assertDoesNotExist()
  assertFalse(state.controlsHidden);assertFalse(runtime.windowMasks.containsKey("observation"));assertFalse(runtime.windowMasks.containsKey("wordmark"));assertTrue(runtime.windowMasks.containsKey("settings_sheet"))
  assertSame(pose,runtime.pose);assertSame(sky,runtime.skyView)
  ui.onNodeWithTag("settings_close").performClick();frame()
  ui.onNodeWithTag("observe_header").assertExists();ui.onNodeWithTag("observation_controls").assertExists();assertSame(pose,runtime.pose);assertSame(sky,runtime.skyView)
 }
 @Test fun allThemesTransparencyAboutAndBackRemainReachable() {
  val runtime=CameraRuntime();var state by mutableStateOf(AppState(theme=AppThemeMode.DARK));var open by mutableStateOf(true)
  ui.setContent{SkyTheme(state.theme){Box(Modifier.fillMaxSize()){if(open)SettingsDialog(state,{state=it(state)},runtime){open=false}}}}
  frame()
  for(mode in AppThemeMode.entries){ui.onNodeWithTag("theme_${mode.name}").performScrollTo().performClick();frame();assertEquals(mode,state.theme);ui.onNodeWithTag("theme_${mode.name}").assertIsSelected()}
  ui.onNodeWithTag("reduce_transparency").performScrollTo().performClick();frame();assertTrue(state.reduceTransparency)
  ui.onNodeWithTag("about_open").performScrollTo().performClick();frame();ui.onNodeWithText("关于 startracker").assertExists()
  ui.runOnUiThread{ui.activity.onBackPressedDispatcher.onBackPressed()};frame();ui.onNodeWithTag("settings_sheet").assertExists()
  ui.runOnUiThread{ui.activity.onBackPressedDispatcher.onBackPressed()};frame();assertFalse(open);assertFalse(runtime.windowMasks.containsKey("settings_sheet"))
 }
}
