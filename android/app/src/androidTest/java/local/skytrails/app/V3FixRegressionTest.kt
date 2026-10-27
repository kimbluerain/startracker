package local.skytrails.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import local.skytrails.app.core.*
import local.skytrails.app.location.ManualLocationInput
import local.skytrails.app.ui.*
import local.skytrails.app.ui.renderer.CameraRuntime
import local.skytrails.app.ui.sky.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Behavioral regressions compiled against fb83f91, before the consolidated production fix. */
class V3FixRegressionTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 @Before fun clock(){ui.mainClock.autoAdvance=false}
 private fun frame(){ui.mainClock.advanceTimeBy(64);ui.waitForIdle()}
 private fun pump(condition:()->Boolean){ui.waitUntil(15000){ui.mainClock.advanceTimeBy(64);condition()}}
 private val at=Instant.parse("2026-10-24T11:00:00Z")
 private val place=ManualLocationInput("fixture","30","120","Asia/Shanghai").selection()
 private fun calculation(time:Instant,d:SkyDirection)=SkyCalculation(place.site,time,d,d,GalacticSky(place.site,time,emptyList(),emptyList(),emptyList(),d),emptyList(),emptyList(),emptyList())
 @Test fun focusWaitsForNewCalculationRatherThanConsumingNull() {
  val runtime=CameraRuntime();var state by mutableStateOf(AppState(location=place,instant=at,live=false,skyFollow=false,virtualSky=true))
  var c by mutableStateOf<SkyCalculation?>(null);val future=at.plusSeconds(3600);val target=SkyDirection(30.0,20.0)
  ui.setContent{VirtualSkyScene(state,c,runtime,null,{state=it(state)},Modifier.fillMaxSize())}
  pump{runtime.skyView?.presented?.get()!=null}
  ui.runOnIdle{state=state.event(SkyTarget.SUN,future)};frame()
  ui.runOnIdle{c=calculation(future,target)};frame()
  pump{runtime.skyView?.presented?.get()?.calculation===c}
  val view=runtime.skyView!!
  val first=view.presented.get()!!;val pending=view.pending.get()
  val firstMark=GalacticOverlay.target(target,first.projection)
  val firstCentered=firstMark is TargetIndicator.Visible && kotlin.math.abs(firstMark.pixel.x-first.projection.width/2.0)<.01 && kotlin.math.abs(firstMark.pixel.y-first.projection.height/2.0)<.01
  androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(2,android.os.Bundle().apply {
   putString("stream","Focus phase A: consumed=${runtime.skyFocus.consumed}, presented=$firstMark, pendingSame=${pending===first}, pending=${pending?.let{GalacticOverlay.target(target,it.projection)}}\n")
  })
  if(!firstCentered) {
   // Matching calculation can be presented before LaunchedEffect's manual-view update.
   // Wait for the next submitted snapshot, not for the assertion itself to become true.
   pump{runtime.skyFocus.consumed==state.focusRequest && view.pending.get()!==first}
   val focused=view.pending.get()!!
   pump{view.presented.get()===focused}
  }
  val projection=runtime.skyView!!.presented.get()!!.projection
  val mark=GalacticOverlay.target(target,projection)
  androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(2,android.os.Bundle().apply {
   putString("stream","Focus final phase: consumed=${runtime.skyFocus.consumed}, newSnapshot=${view.presented.get()!==first}, presented=$mark\n")
  })
  assertTrue("Late matching calculation must apply the pending focus",mark is TargetIndicator.Visible)
  val pixel=(mark as TargetIndicator.Visible).pixel
  assertEquals(projection.width/2.0,pixel.x,.01);assertEquals(projection.height/2.0,pixel.y,.01)
 }
 @Test fun staticManualSceneStopsSubmittingAndDrawingAfterSettling() {
  val runtime=CameraRuntime();val state=AppState(virtualSky=true,skyFollow=false)
  ui.setContent{VirtualSkyScene(state,null,runtime,null,{},Modifier.fillMaxSize())}
  pump{runtime.skyView?.presented?.get()!=null}
  repeat(5){frame();Thread.sleep(25)}
  val before=runtime.skyView!!.frames
  repeat(15){frame();Thread.sleep(25)}
  assertEquals("No pose/state changes: GL must remain idle",before,runtime.skyView!!.frames)
 }
 @Test fun modalSuppressesBehindGuidanceWithRealCalculationAndRestoresIt() {
  val runtime=CameraRuntime().apply{manualSkyBasis=SkyViewBasis.from(SkyProjection.virtual(100,100,70.0,0.0,0.0))}
  val state=AppState(location=place,instant=at,live=false,virtualSky=true,skyFollow=false,target=SkyTarget.MOON)
  val c=calculation(at,SkyDirection(180.0,0.0));var settings by mutableStateOf(false)
  ui.setContent{SkyTheme(AppThemeMode.DARK){Box(Modifier.fillMaxSize()){
   ObserveScreen(state,c,runtime,{},{},{},{},{settings=true},{},hudVisible=!settings)
   if(settings)SettingsDialog(state,{},runtime){settings=false}
  }}}
  pump{runtime.skyView?.presented?.get()?.calculation===c}
  ui.onNodeWithTag("focused_target_guidance").assertExists()
  ui.onNodeWithTag("settings").performClick();frame()
  ui.onNodeWithTag("focused_target_guidance").assertDoesNotExist()
  ui.onNodeWithTag("settings_close").performClick();frame()
  ui.onNodeWithTag("focused_target_guidance").assertExists()
 }
 @Test fun galaxyBehindLeavesTheCenterClearWithRealSkyAndHorizon() {
  val geo=MilkyWayGeometry();val galaxy=geo.sky(place.site,at)
  val engine=AstronomyEngineAdapter(geo)
  val c=SkyCalculation(place.site,at,engine.position(local.skytrails.app.core.Target.SUN,place.site,at,Atmosphere.AIRLESS),engine.position(local.skytrails.app.core.Target.MOON,place.site,at,Atmosphere.AIRLESS),galaxy,emptyList(),emptyList(),emptyList(),StarCatalog.sky(place.site,at))
  val runtime=CameraRuntime().apply{manualSkyBasis=SkyViewBasis.from(SkyProjection.virtual(100,100,70.0,galaxy.center.azimuthDeg+180.0,-galaxy.center.altitudeDeg))}
  val state=AppState(location=place,instant=at,live=false,virtualSky=true,skyFollow=false,target=SkyTarget.GALAXY,controlsHidden=true,layers=SkyLayers(false,false,true),starLayers=StarLayers(false,false,false,emptySet(),masterVisible=true,twinkle=false))
  ui.setContent{SkyTheme(AppThemeMode.DARK){ObserveScreen(state,c,runtime,{},{},{},{},{},{})}}
  pump{runtime.skyView?.presented?.get()?.calculation===c}
  val presented=runtime.skyView!!.presented.get()!!
  assertNotNull("Real horizon remains in the frame",presented.up)
  assertTrue(GalacticOverlay.target(galaxy.center,presented.projection) is TargetIndicator.Behind)
  ui.onNodeWithTag("focused_target_guidance").assertDoesNotExist()
  repeat(20){frame();Thread.sleep(80)}
  val inst=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
  val bitmap=inst.uiAutomation.takeScreenshot()!!
  val directory=java.io.File(inst.targetContext.getExternalFilesDir(null),"v12-preview").apply{mkdirs()}
  try{java.io.File(directory,"galaxy-behind-no-hint.png").outputStream().use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}}finally{bitmap.recycle()}
 }

}
