package local.skytrails.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import local.skytrails.app.core.*
import local.skytrails.app.ui.*
import local.skytrails.app.ui.renderer.*
import local.skytrails.app.ui.sky.VirtualSkyScene
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class GalaxyTilesIntegrationTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 @Test fun tileBudgetAndDecodeWorkRemainBoundedAcrossBackgroundAndContextLoss() {
  ui.mainClock.autoAdvance=false
  val runtime=CameraRuntime().apply{skyFieldOfView=20.0};var visible by mutableStateOf(true)
  val site=Site(30.2741,120.1551,0.0,0.0,ZoneId.of("Asia/Shanghai"));val at=Instant.parse("2026-06-15T15:00:00Z")
  val galaxy=MilkyWayGeometry().sky(site,at)
  val calc=SkyCalculation(site,at,galaxy.center,galaxy.center,galaxy,emptyList(),emptyList(),emptyList())
  val state=AppState(virtualSky=true,skyFollow=false,starLayers=StarLayers(masterVisible=false))
  ui.setContent{if(visible)VirtualSkyScene(state,calc,runtime,null,{},Modifier.size(240.dp,420.dp))else Box(Modifier.fillMaxSize())}
  fun settled(){ui.waitUntil(30000){ui.mainClock.advanceTimeBy(64);runtime.skyView?.let{it.presented.get()!=null&&it.tileResidentBytes>0&&it.tileIdle}==true}}
  settled();val view=runtime.skyView!!
  assertNull(view.error);assertNull(view.tileError);assertTrue(view.tileResidentBytes<=48L*1024*1024);assertTrue(view.tilePendingJobs<=47)
  assertEquals(2048 to 1024,view.loadedTextureSizes["galaxy-fallback.jpg"]);assertEquals(99049,view.loadedFieldStarCount)
  val generation=view.contextGeneration;val decodes=view.tileDecodeCount
  ui.runOnUiThread{view.preserveEGLContextOnPause=false}
  ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
  ui.waitUntil(5000){view.tileIdle};val stopped=view.tileDecodeCount;val stoppedFrames=view.frames;Thread.sleep(300)
  assertEquals(stopped,view.tileDecodeCount);assertEquals(stoppedFrames,view.frames)
  ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
  ui.waitUntil(15000){ui.mainClock.advanceTimeBy(64);view.contextGeneration>generation};settled()
  assertNull(view.tileError);assertTrue(view.tileResidentBytes<=GalaxyTiles.BUDGET_BYTES);assertTrue(view.tileDecodeCount>0);assertTrue(decodes>0)
  ui.runOnUiThread{visible=false};ui.mainClock.advanceTimeBy(64);ui.waitForIdle()
  ui.waitUntil(5000){view.tileIdle};val disposed=view.tileDecodeCount;Thread.sleep(300);assertEquals(disposed,view.tileDecodeCount)
 }
}
