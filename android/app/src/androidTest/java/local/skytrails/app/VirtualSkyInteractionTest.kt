package local.skytrails.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import local.skytrails.app.ar.*
import local.skytrails.app.core.SkyDirection
import local.skytrails.app.location.ManualLocationInput
import local.skytrails.app.ui.*
import local.skytrails.app.ui.renderer.CameraRuntime
import local.skytrails.app.ui.sky.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class VirtualSkyInteractionTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 @Before fun clock(){ui.mainClock.autoAdvance=false}
 private fun frame(){ui.mainClock.advanceTimeBy(64);ui.waitForIdle()}
 private fun pump(condition:()->Boolean){ui.waitUntil(15000){ui.mainClock.advanceTimeBy(64);condition()}}
 private val onsite=ManualLocationInput("synthetic onsite","30","120","Asia/Shanghai",confirmedOnSite=true).selection()
 private fun pose()=PoseFrame(PhysicalSite(30.0,120.0,1.6),Instant.now(),doubleArrayOf(1.0,0.0,0.0,0.0,0.0,-1.0,0.0,1.0,0.0),0.0)
 @Test fun sensorFollowDragManualLayerToggleAndRestoreFollowUsePresentedSnapshots() {
  val runtime=CameraRuntime();var state by mutableStateOf(AppState(location=onsite,virtualSky=true,theme=AppThemeMode.DARK))
  ui.setContent{SkyTheme(state.theme){ObserveScreen(state,null,runtime,{state=it(state)},{},{},{},{},{})}}
  pump{runtime.pose!=null&&runtime.skyView!=null}
  ui.runOnIdle{runtime.pose!!.stop();runtime.pose!!.frame.set(pose())}
  pump{runtime.skyView!!.presented.get()?.let{val p=it.projection;val mark=GalacticOverlay.target(SkyDirection(0.0,0.0),p);mark is TargetIndicator.Visible&&kotlin.math.abs(mark.pixel.x-p.width/2.0)<.01}==true}
  assertTrue(state.skyFollow)
  val before=runtime.skyView!!.presented.get()!!.projection
  ui.onNodeWithTag("virtual_sky_gl").performTouchInput{swipe(Offset(width*.42f,height*.43f),Offset(width*.61f,height*.45f),300)};frame()
  assertFalse(state.skyFollow)
  pump{runtime.skyView!!.presented.get()!!.projection!==before}
  ui.waitUntil(5000){runtime.skyView!!.pending.get()===runtime.skyView!!.presented.get()}
  val manual=runtime.skyView!!.presented.get()!!.projection
  val point=GalacticOverlay.target(SkyDirection(0.0,0.0),manual)
  ui.onNodeWithTag("category_SUN").performClick();frame();ui.onNodeWithTag("display_layer_SUN").performClick();frame()
  assertFalse(state.skyFollow);assertEquals(0L,state.focusRequest)
  ui.waitUntil(5000){runtime.skyView!!.pending.get()===runtime.skyView!!.presented.get()}
  assertEquals(point,GalacticOverlay.target(SkyDirection(0.0,0.0),runtime.skyView!!.presented.get()!!.projection))
  ui.onNodeWithTag("sky_follow").performClick();frame();assertTrue(state.skyFollow)
  pump{runtime.skyView!!.presented.get()?.let{val p=it.projection;val mark=GalacticOverlay.target(SkyDirection(0.0,0.0),p);mark is TargetIndicator.Visible&&kotlin.math.abs(mark.pixel.x-p.width/2.0)<.01}==true}
 }
 @Test fun leavingObserveDisposesPoseAndVirtualRendererAndLifecycleStopClearsSensorFrame() {
  val runtime=CameraRuntime();var visible by mutableStateOf(true)
  val state=AppState(location=onsite,virtualSky=true)
  ui.setContent{if(visible)SkyTheme(state.theme){ObserveScreen(state,null,runtime,{},{},{},{},{},{})}else Box(Modifier.fillMaxSize())}
  pump{runtime.pose!=null&&runtime.skyView!=null};val adapter=runtime.pose!!
  ui.runOnIdle{adapter.frame.set(pose())}
  ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
  assertFalse(adapter.running);assertNull(adapter.frame.get())
  ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);frame()
  assertSame(adapter,runtime.pose)
  ui.runOnIdle{visible=false};frame()
  assertNull(runtime.pose);assertNull(runtime.skyView);assertNull(runtime.view);assertFalse(adapter.running)
 }
 @Test fun absentPhysicalContextUsesManualAndExplainsDisabledFollow() {
  val runtime=CameraRuntime();val state=AppState(virtualSky=true)
  ui.setContent{SkyTheme(state.theme){ObserveScreen(state,null,runtime,{},{},{},{},{},{})}}
  frame();ui.onNodeWithTag("sky_follow").assertIsNotEnabled()
  ui.onNodeWithTag("observe_status").performClick();frame()
  ui.onNodeWithText("手动浏览：需要有效现场位置和方向传感器才能跟随手机。").assertExists()
 }
}
