package local.skytrails.app

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import local.skytrails.app.location.ManualLocationInput
import local.skytrails.app.ui.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant

/** One local visual fixture, never phone data or a platform compatibility matrix. */
class ObservationControlsPreviewTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 @Before fun clock(){ui.mainClock.autoAdvance=false;ui.runOnUiThread{ui.activity.enableEdgeToEdge()}}
 private fun frame(){ui.mainClock.advanceTimeBy(64);ui.waitForIdle()}
 private fun pump(condition:()->Boolean){ui.waitUntil(15000){ui.mainClock.advanceTimeBy(64);condition()}}
 private fun settle(){repeat(10){frame();Thread.sleep(60)}}
 @Test fun clearTimeActionAndPanelRecoveryWithActualScreenshots() {
  lateinit var model:SkyViewModel
  ui.runOnUiThread{model=ViewModelProvider(ui.activity)[SkyViewModel::class.java]}
  ui.setContent{SkyApp(model,{},{},{})};pump{model.settingsReady.value}
  val saved=model.state.value
  val at=Instant.parse("2026-10-26T20:00:00Z")
  val place=ManualLocationInput("杭州 · 公开示例","30.2741","120.1551","Asia/Shanghai").selection()
  try {
   ui.runOnIdle{model.select(place);model.change{it.copy(theme=AppThemeMode.DARK,reduceTransparency=false,virtualSky=true,skyFollow=false,live=false,instant=at,controlsHidden=false,layers=SkyLayers(false,false,true)).focusConstellation("orion").copy(observationCategory=DisplayLayer.MOON)}}
   pump{model.calculation.value?.at==at};settle()
   ui.onNodeWithText("回到现在").assertIsDisplayed()
   ui.onNodeWithText("收起面板").assertIsDisplayed()
   capture("01-time-preview")
   val prior=model.state.value
   val before=Instant.now()
   ui.onNodeWithTag("return_now").performClick();frame()
   assertTrue(model.state.value.live)
   assertFalse(model.state.value.instant.isBefore(before))
   assertEquals(prior.layers,model.state.value.layers);assertEquals(prior.skyFollow,model.state.value.skyFollow)
   ui.onNodeWithText("实时更新").assertIsDisplayed()
   ui.onNodeWithTag("return_now").assertDoesNotExist()
   val first=model.state.value.instant;pump{model.state.value.instant.isAfter(first)};settle();capture("02-live")
   ui.onNodeWithText("收起面板").performClick();frame()
   ui.onNodeWithTag("observation_controls").assertDoesNotExist();ui.onNodeWithTag("bottom_navigation").assertDoesNotExist()
   ui.onNodeWithText("展开面板").assertIsDisplayed();settle();capture("03-collapsed")
   ui.onNodeWithText("展开面板").performClick();frame()
   assertEquals(prior.observationCategory,model.state.value.observationCategory)
   for(target in SkyTarget.entries){ui.onNodeWithTag("category_${target.name}").performClick();frame();val switch=ui.onNodeWithTag("display_layer_${target.name}");if(target==SkyTarget.GALAXY)switch.assertIsOn()else switch.assertIsOff()}
   ui.onNodeWithTag("time_slider").assertIsDisplayed();ui.onNodeWithTag("bottom_navigation").assertExists()
   // Match the existing SkyNavigation fixture: avoid the clipped x=0/width edges.
   ui.onNodeWithTag("time_slider").performTouchInput{swipe(Offset(width*.2f,centerY),Offset(width*.8f,centerY),400)};frame()
   assertFalse(model.state.value.live);ui.onNodeWithText("回到现在").assertIsDisplayed()
   ui.runOnIdle{model.change{it.copy(theme=AppThemeMode.NIGHT,reduceTransparency=true)}};settle();capture("04-night-opaque")
   ui.onNodeWithText("收起面板").assertIsDisplayed();ui.onNodeWithText("回到现在").assertIsDisplayed()
  } finally {ui.runOnIdle{model.changeSettings{it.copy(theme=saved.theme,reduceTransparency=saved.reduceTransparency,layers=saved.layers,starLayers=saved.starLayers)}};frame()}
 }
 private fun capture(name:String) {
  val i=InstrumentationRegistry.getInstrumentation();val bitmap=i.uiAutomation.takeScreenshot()?:error("No window")
  val dir=File(i.targetContext.getExternalFilesDir(null),"v5-preview").apply{mkdirs()}
  File(dir,"$name.png").outputStream().use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
 }
}
