package local.skytrails.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import local.skytrails.app.core.*
import local.skytrails.app.location.*
import local.skytrails.app.ui.*
import local.skytrails.app.ui.renderer.CameraRuntime
import local.skytrails.app.ui.sky.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant

class FinalSkyInteractionTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 @Before fun manualClock(){ui.mainClock.autoAdvance=false}
 private fun frame(){ui.mainClock.advanceTimeBy(64);ui.waitForIdle()}
 private fun pump(condition:()->Boolean){ui.waitUntil(15000){ui.mainClock.advanceTimeBy(64);condition()}}
 private val at=Instant.parse("2026-10-24T11:30:00Z")
 private fun onsite()=ManualLocationInput("fixture","30","120","Asia/Shanghai",confirmedOnSite=true).selection()

 @Test fun oldOrLatePhotoCannotDisplayOrPublishGlassForNewRequest() {
  val a=SkyRenderRequest(SkyDirectionRequest(null,null),0f,0f,70.0,32,32,true,false,.7f,Any())
  val b=a.copy(azimuth=90f);val c=b.copy(night=true,galaxy=false)
  fun bitmap(color:Int)=Bitmap.createBitmap(IntArray(32*32){color},32,32,Bitmap.Config.ARGB_8888)
  val red=bitmap(0xffff0000.toInt());val green=bitmap(0xff00ff00.toInt());val blue=bitmap(0xff0000ff.toInt())
  var expected by mutableStateOf(a)
  var result by mutableStateOf<RequestedSkyResult<SkyRenderRequest,Bitmap>?>(RequestedSkyResult(a,red))
  val glass=FrostedBackdrop()
  ui.setContent{CompositionLocalProvider(LocalFrostedBackdrop provides glass){
   Box(Modifier.size(120.dp).background(Color.Black).testTag("matched_photo")){SkyBackdropImage(expected,result,Modifier.fillMaxSize())}
  }}
  fun center():Color {val p=ui.onNodeWithTag("matched_photo").captureToImage().toPixelMap();return p[p.width/2,p.height/2]}
  pump{glass.frame!=null};assertTrue(center().red>.9f)
  ui.runOnIdle{expected=b};frame();assertNull(glass.frame);assertEquals(Color.Black,center())
  ui.runOnIdle{result=RequestedSkyResult(b,green)};pump{glass.frame!=null};assertTrue(center().green>.9f)
  // Simulate a non-cooperative late completion: cancellation is not the acceptance guard.
  ui.runOnIdle{result=RequestedSkyResult(a,red)};frame();assertNull(glass.frame);assertEquals(Color.Black,center())
  ui.runOnIdle{expected=c;result=RequestedSkyResult(b,green)};frame();assertNull(glass.frame);assertEquals(Color.Black,center())
  ui.runOnIdle{result=RequestedSkyResult(c,blue)};pump{glass.frame!=null};assertTrue(center().blue>.9f)
 }

 @Test fun nightControlsRemainRedForOnOffLayersPreviewLiveAndHidden() {
  var s by mutableStateOf(AppState(theme=AppThemeMode.NIGHT,reduceTransparency=true,instant=at,realNow=at,live=false))
  ui.setContent{SkyTheme(AppThemeMode.NIGHT){ObserveScreen(s,null,remember{CameraRuntime()},{s=it(s)},{},{},{},{},{})}}
  frame()
  val choices=listOf(SkyLayers(true,false,true),SkyLayers(false,false,false),SkyLayers())
  for((i,layers)in choices.withIndex())for(live in listOf(false,true)) {
   ui.runOnIdle{s=s.copy(layers=layers,live=live)};frame()
   for(target in SkyTarget.entries)assertRedNode("category_${target.name}","night-$i-$live-${target.name}")
   assertRedNode("time_slider","night-$i-$live-timeline")
   assertRedNode("observe_status","night-$i-$live-status")
   assertRedNode("mode_toggle","night-$i-$live-mode")
   if(!live)assertRedNode("preview_label","night-$i-preview")
  }
  ui.onNodeWithTag("hide_controls").performClick();frame()
  ui.onNodeWithTag("observation_controls").assertDoesNotExist()
  assertRedNode("restore_controls","night-hidden-restore")
  assertRedNode("observe_status","night-hidden-status")
  ui.onNodeWithTag("restore_controls").performClick();frame();ui.onNodeWithTag("observation_controls").assertIsDisplayed()
 }

 @Test fun explicitSameDifferentAndHiddenFocusSelectionsAlwaysRecenterWithoutEnablingLayers() {
  val initial=SkyLayers(false,true,false)
  var s by mutableStateOf(AppState(target=SkyTarget.GALAXY,layers=initial))
  val position=SkyDirection(0.0,0.0);val site=onsite().site
  val computed=SkyCalculation(site,at,position,position,GalacticSky(site,at,emptyList(),emptyList(),emptyList(),position),emptyList(),emptyList(),emptyList())
  ui.setContent{SkyTheme(AppThemeMode.DARK){ObserveScreen(s,computed,remember{CameraRuntime()},{s=it(s)},{},{},{},{},{})}}
  frame()
  for((i,target)in listOf(SkyTarget.GALAXY,SkyTarget.SUN,SkyTarget.SUN,SkyTarget.MOON).withIndex()) {
   ui.onNodeWithTag("category_${target.name}").performClick();frame();ui.onNodeWithTag("focus_${target.name}").performClick();frame()
   assertEquals(target,s.target);assertEquals(i+1L,s.focusRequest);assertEquals(initial,s.layers)
  }
 }

 @Test fun arIntentIsRecordedBeforePermissionAndGrantEntersCameraWhileExpiryFallsBack() {
  var s by mutableStateOf(AppState(location=onsite(),instant=at,realNow=at,virtualSky=true,cameraPermission=false))
  var requested=0;var intentAtRequest=true
  ui.setContent{SkyTheme(AppThemeMode.DARK){ObserveScreen(s,null,remember{CameraRuntime()},{s=it(s)},{},{},{},{},{requested++;intentAtRequest=s.virtualSky})}}
  frame();ui.onNodeWithTag("mode_toggle").performClick();frame()
  assertEquals(1,requested);assertFalse(intentAtRequest);ui.onNodeWithTag("camera_host").assertDoesNotExist()
  ui.runOnIdle{s=s.copy(cameraPermission=false)};frame();ui.onNodeWithTag("camera_host").assertDoesNotExist()
  ui.runOnIdle{s=s.copy(cameraPermission=true)};frame();ui.onNodeWithTag("camera_host").assertExists()
  val expired=onsite().copy(source=LocationSource.DEVICE,fixAt=at.minusSeconds(901))
  ui.runOnIdle{s=s.copy(location=expired,locationPermission=true)};frame();ui.onNodeWithTag("camera_host").assertDoesNotExist()
 }

 @Test fun focusedBehindHintUsesTheCurrentProjectionWithoutBottomControlsOrHiddenLayers() {
  val site=onsite().site;val behind=SkyDirection(180.0,0.0)
  val c=SkyCalculation(site,at,behind,behind,GalacticSky(site,at,emptyList(),emptyList(),emptyList(),behind),emptyList(),emptyList(),emptyList())
  var heading by mutableFloatStateOf(0f);var layers by mutableStateOf(SkyLayers());var target by mutableStateOf(SkyTarget.GALAXY)
  ui.setContent{Box(Modifier.fillMaxSize().background(Color.Black)){
   SkyCanvas(c,target,false,Modifier.fillMaxSize(),SkyProjection.virtual(800,1200,70.0,heading.toDouble(),0.0),physical=true,layers=layers)
  }}
  frame();ui.onNodeWithTag("focused_target_guidance").assertDoesNotExist()
  ui.runOnIdle{heading=180f};frame();ui.onNodeWithTag("focused_target_guidance").assertDoesNotExist()
  ui.runOnIdle{heading=120f};frame();ui.onNodeWithTag("focused_target_guidance").assertDoesNotExist()
  ui.runOnIdle{heading=0f;layers=layers.copy(galaxy=false)};frame();ui.onNodeWithTag("focused_target_guidance").assertDoesNotExist()
  ui.runOnIdle{target=SkyTarget.MOON};frame();ui.onNodeWithTag("focused_target_guidance").assertTextEquals("月亮在身后 · 转身寻找")
 }

 private fun assertRedNode(tag:String,name:String) {
  val image=ui.onNodeWithTag(tag).captureToImage();val pixels=image.toPixelMap()
  val directory=File(ui.activity.getExternalFilesDir(null),"validation/final-night").apply{mkdirs()}
  File(directory,"$name.png").outputStream().use{image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
  var lit=0;var nonRed=0
  for(y in 0 until pixels.height step 2)for(x in 0 until pixels.width step 2){val color=pixels[x,y]
   if(color.red+color.green+color.blue>.14f){lit++;if(color.red<color.green*1.4f||color.red<color.blue*1.4f)nonRed++}
  }
  assertTrue("$tag must contain visible control pixels",lit>20)
  assertTrue("$tag must stay red: $nonRed / $lit",nonRed<lit*.005)
 }
}
