package local.skytrails.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import local.skytrails.app.ar.*
import local.skytrails.app.ui.*
import local.skytrails.app.ui.renderer.CameraRuntime
import local.skytrails.app.ui.sky.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.math.tan

class SkyZoomInteractionTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 private fun pump(condition:()->Boolean){ui.waitUntil(15000){ui.mainClock.advanceTimeBy(64);condition()}}
 private fun settle(runtime:CameraRuntime){pump{runtime.skyView?.let{it.pending.get()===it.presented.get()&&it.presented.get()!=null}==true}}
 private fun pinch(tail:Boolean=false) {
  ui.onNodeWithTag("virtual_sky_gl").performTouchInput {
   val y=height*.45f
   down(0,Offset(width*.35f,y));down(1,Offset(width*.65f,y))
   for(i in 1..10){moveTo(0,Offset(width*(.35f-.15f*i/10),y));moveTo(1,Offset(width*(.65f+.15f*i/10),y))}
   up(1)
   if(tail)moveTo(0,Offset(width*.15f,y+height*.1f))
   up(0)
  }
 }
 @Test fun pinchKeepsSensorFollowingIncludingRemainingFingerAndSharesFovWithPhoto() {
  ui.mainClock.autoAdvance=false
  val r=CameraRuntime();var state by mutableStateOf(AppState(virtualSky=true,skyFollow=true))
  val pose=PoseFrame(PhysicalSite(30.0,120.0,0.0),Instant.now(),doubleArrayOf(1.0,0.0,0.0,0.0,0.0,-1.0,0.0,1.0,0.0),0.0)
  ui.setContent{VirtualSkyScene(state,null,r,pose,{state=it(state)},Modifier.fillMaxSize())}
  settle(r);val first=r.skyView!!.presented.get()!!
  val x0=first.projection.toPixel(CameraVector(.15,0.0,1.0))!!.x-first.projection.width/2.0
  pinch(tail=true);settle(r)
  val after=r.skyView!!.presented.get()!!
  assertTrue("Two fingers and the remaining finger must not stop follow",state.skyFollow)
  assertTrue(r.skyFieldOfView<50.0);assertEquals(r.skyFieldOfView,after.horizontalFov,0.0)
  val x1=after.projection.toPixel(CameraVector(.15,0.0,1.0))!!.x-after.projection.width/2.0
  assertEquals(2.0,x1/x0,.025)
  assertEquals(tan(Math.toRadians(first.horizontalFov/2))/tan(Math.toRadians(after.horizontalFov/2)),x1/x0,1e-6)
 }
 @Test fun dragEntersManualAndZoomAndOrientationSurviveRendererReentry() {
  ui.mainClock.autoAdvance=false
  val r=CameraRuntime();var state by mutableStateOf(AppState(virtualSky=true,skyFollow=true));var visible by mutableStateOf(true)
  ui.setContent{if(visible)VirtualSkyScene(state,null,r,null,{state=it(state)},Modifier.fillMaxSize())}
  settle(r)
  ui.onNodeWithTag("virtual_sky_gl").performTouchInput{swipe(Offset(width*.4f,height*.45f),Offset(width*.6f,height*.45f),250)}
  settle(r);assertFalse(state.skyFollow)
  pinch();settle(r);val fov=r.skyFieldOfView;val basis=r.manualSkyBasis
  ui.runOnIdle{visible=false};ui.mainClock.advanceTimeBy(64);ui.waitForIdle();assertNull(r.skyView)
  ui.runOnIdle{visible=true};settle(r)
  assertEquals(fov,r.skyView!!.presented.get()!!.horizontalFov,0.0);assertEquals(basis,r.manualSkyBasis)
 }
}
