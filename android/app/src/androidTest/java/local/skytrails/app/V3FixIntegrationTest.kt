package local.skytrails.app

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import local.skytrails.app.ar.*
import local.skytrails.app.core.*
import local.skytrails.app.location.ManualLocationInput
import local.skytrails.app.ui.*
import local.skytrails.app.ui.renderer.*
import local.skytrails.app.ui.sky.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class V3FixIntegrationTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 @Before fun clock(){ui.mainClock.autoAdvance=false}
 private fun frame(){ui.mainClock.advanceTimeBy(64);ui.waitForIdle()}
 private fun pump(condition:()->Boolean){ui.waitUntil(15000){ui.mainClock.advanceTimeBy(64);condition()}}
 private fun settled(r:CameraRuntime){ui.waitUntil(5000){r.skyView?.pending?.get()===r.skyView?.presented?.get()};Thread.sleep(50)}
 private val at=Instant.parse("2026-10-24T11:00:00Z")
 private val place=ManualLocationInput("fixture","30","120","Asia/Shanghai").selection()
 private fun calculation(time:Instant,d:SkyDirection)=SkyCalculation(place.site,time,d,d,GalacticSky(place.site,time,emptyList(),emptyList(),emptyList(),d),emptyList(),emptyList(),emptyList())
 @Test fun lateOldFocusIsIgnoredAndConsumedFocusDoesNotOverrideDragOnRendererReentry() {
  val r=CameraRuntime();var visible by mutableStateOf(true);var state by mutableStateOf(AppState(location=place,instant=at,live=false,skyFollow=false,virtualSky=true))
  var c by mutableStateOf<SkyCalculation?>(null);val future=at.plusSeconds(3600);val target=SkyDirection(30.0,20.0)
  ui.setContent{if(visible)VirtualSkyScene(state,c,r,null,{state=it(state)},Modifier.fillMaxSize())else Box(Modifier.fillMaxSize())}
  pump{r.skyView?.presented?.get()!=null};ui.runOnIdle{state=state.event(SkyTarget.SUN,future);c=calculation(at,SkyDirection(80.0,5.0))};frame()
  assertEquals(0L,r.skyFocus.consumed)
  ui.runOnIdle{c=calculation(future,target)};frame();pump{r.skyFocus.consumed==1L};settled(r)
  ui.onNodeWithTag("virtual_sky_gl").performTouchInput{swipe(Offset(width*.4f,height*.4f),Offset(width*.6f,height*.43f),300)};frame();settled(r)
  val dragged=GalacticOverlay.target(target,r.skyView!!.presented.get()!!.projection)
  ui.runOnIdle{visible=false};frame();ui.runOnIdle{visible=true};frame();pump{r.skyView?.presented?.get()!=null};settled(r)
  assertEquals("Reentry preserves dragged orientation",dragged,GalacticOverlay.target(target,r.skyView!!.presented.get()!!.projection))
  ui.runOnIdle{state=state.focus(SkyTarget.SUN)};frame();pump{r.skyFocus.consumed==2L};settled(r)
  val p=r.skyView!!.presented.get()!!.projection;val mark=GalacticOverlay.target(target,p) as TargetIndicator.Visible
  assertEquals(p.width/2.0,mark.pixel.x,.01);assertEquals(p.height/2.0,mark.pixel.y,.01)
 }
 @Test fun staticCountersStayStillButMaskBlurAndPoseInputsEachRedraw() {
  val r=CameraRuntime();var pose by mutableStateOf<PoseFrame?>(null);val state=AppState(virtualSky=true,skyFollow=true)
  ui.setContent{VirtualSkyScene(state,null,r,pose,{},Modifier.fillMaxSize())}
  pump{r.skyView?.presented?.get()!=null};settled(r);val view=r.skyView!!;val before=view.frames;val blur=view.blurPasses
  repeat(8){frame();Thread.sleep(20)};assertEquals(before,view.frames);assertEquals(blur,view.blurPasses)
  ui.runOnIdle{r.windowMasks["fixture"]=GlassMask(20f,20f,140f,140f,8f);r.masks()}
  ui.waitUntil(5000){view.frames>before};assertTrue(view.blurPasses>blur);val masked=view.frames
  repeat(5){frame();Thread.sleep(20)};assertEquals(masked,view.frames)
  ui.runOnIdle{view.blurEnabled=false};ui.waitUntil(5000){view.frames>masked};val noBlur=view.blurPasses;val disabled=view.frames
  repeat(5){frame();Thread.sleep(20)};assertEquals(disabled,view.frames);assertEquals(noBlur,view.blurPasses)
  ui.runOnIdle{pose=PoseFrame(PhysicalSite(30.0,120.0,0.0),Instant.now(),doubleArrayOf(1.0,0.0,0.0,0.0,0.0,-1.0,0.0,1.0,0.0),0.0)};frame();pump{view.frames>disabled}
  val projected=view.pending.get()!!.projection;val north=GalacticOverlay.target(SkyDirection(0.0,0.0),projected) as TargetIndicator.Visible
  assertEquals(projected.width/2.0,north.pixel.x,.01)
 }
 @Test fun actualSkyGlassViewRecreatesTexturesAndRendersSamePixelsAfterContextLoss() {
  val r=CameraRuntime();val state=AppState(virtualSky=true,skyFollow=false)
  ui.setContent{VirtualSkyScene(state,null,r,null,{},Modifier.fillMaxSize())}
  pump{r.skyView?.presented?.get()!=null};settled(r);val view=r.skyView!!
  val generation=view.contextGeneration;val before=copy(view)
  ui.runOnUiThread{view.preserveEGLContextOnPause=false}
  ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED);Thread.sleep(200)
  ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
  pump{view.contextGeneration>generation&&view.presented.get()!=null};settled(r)
  assertSame(view,r.skyView);assertNull(view.error);val after=copy(view)
  for(y in 0 until before.height step 19)for(x in 0 until before.width step 17)assertEquals("Restored View pixel $x,$y",before.getPixel(x,y),after.getPixel(x,y))
  before.recycle();after.recycle()
 }
 private fun copy(view:SkyGlassView):Bitmap {
  val result=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);val latch=CountDownLatch(1);var status=-1
  val handler=Handler(Looper.getMainLooper());handler.post{PixelCopy.request(view,result,{status=it;latch.countDown()},handler)}
  assertTrue(latch.await(5,TimeUnit.SECONDS));assertEquals(PixelCopy.SUCCESS,status);return result
 }
}
