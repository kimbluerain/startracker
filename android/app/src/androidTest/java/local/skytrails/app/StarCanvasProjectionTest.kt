package local.skytrails.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import local.skytrails.app.ui.renderer.GlassMask
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import local.skytrails.app.core.*
import local.skytrails.app.ui.*
import local.skytrails.app.ui.sky.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.*
import kotlin.math.*

class StarCanvasProjectionTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 @Test fun physicalCanvasAddsFourSoftSpikesOnlyWhileTheirLayerIsVisible() {
  ui.mainClock.autoAdvance=false
  val at=Instant.EPOCH;val site=Site(0.0,0.0,0.0,0.0,ZoneId.of("UTC"));val d=SkyDirection(0.0,18.0)
  val stars=StarSky(site,at,mapOf(24436 to d),mapOf("orion" to d))
  val c=SkyCalculation(site,at,d,d,GalacticSky(site,at,emptyList(),emptyList(),emptyList(),d),emptyList(),emptyList(),emptyList(),stars)
  val initial=StarLayers(true,false,false,setOf("orion"))
  var layers by mutableStateOf(initial);var masks by mutableStateOf<List<GlassMask>>(emptyList());var night by mutableStateOf(false);var scale=1f;var origin=Offset.Zero
  ui.setContent{scale=LocalDensity.current.density;BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).onGloballyPositioned{origin=it.boundsInWindow().topLeft}){
   val projection=SkyProjection.virtual(constraints.maxWidth,constraints.maxHeight,70.0,0.0,18.0)
   SkyCanvas(c,SkyTarget.SUN,night,Modifier.fillMaxSize().testTag("star_spike_canvas"),cameraProjection=projection,physical=true,exclusions=masks,layers=SkyLayers(false,false,false),showGuidance=false,starLayers=layers)
  }}
  fun frame(){ui.mainClock.advanceTimeBy(64);ui.waitForIdle()}
  var canvasCenter=Offset.Zero
  // Retain only the sampled star region, not 24 full-screen pixel buffers on high-density devices.
  fun capture():PixelMap {
   frame()
   val image=ui.onNodeWithTag("star_spike_canvas").captureToImage()
   canvasCenter=Offset(image.width/2f,image.height/2f)
   val radius=(20*scale).roundToInt()+4
   return try {image.toPixelMap(startX=image.width/2-radius,startY=image.height/2-radius,width=radius*2+1,height=radius*2+1)}
   finally {image.asAndroidBitmap().recycle()}
  }
  val off=capture();val x=off.width/2;val y=off.height/2;val distance=(8*scale).roundToInt()
  val axes=listOf(distance to 0,-distance to 0,0 to distance,0 to -distance)
  fun peak(p:PixelMap,dx:Int=0,dy:Int=0,channel:Int=0)=(-1..1).flatMap{oy->(-1..1).map{ox->val color=p[x+dx+ox,y+dy+oy];when(channel){1->color.green;2->color.blue;else->color.red}}}.max()
  ui.runOnIdle{layers=initial.copy(twinkle=true)}
  val frames=(0 until 12).map{Thread.sleep(180);capture()}
  val on=frames.maxBy{p->axes.sumOf{(dx,dy)->peak(p,dx,dy).toDouble()}}
  val diagonal=peak(on,distance,distance)-peak(off,distance,distance)
  axes.forEach{(dx,dy)->assertTrue("Canvas spike at $dx,$dy must extend beyond static halo",peak(on,dx,dy)-peak(off,dx,dy)>diagonal+.012f)}
  assertTrue("Outer tips fade",peak(on,distance,0)>peak(on,(14*scale).roundToInt(),0)+.012f)
  assertTrue("The astronomical center remains lit",peak(on)>.45f)
  ui.runOnIdle{layers=initial}
  val steady=capture();axes.forEach{(dx,dy)->assertEquals(peak(off,dx,dy),peak(steady,dx,dy),.005f)}
  for(hidden in listOf(initial.copy(twinkle=true,masterVisible=false),initial.copy(twinkle=true,stars=false),initial.copy(twinkle=true,enabled=emptySet()))) {
   ui.runOnIdle{layers=hidden};val image=capture();assertTrue(peak(image)<.02f);axes.forEach{(dx,dy)->assertTrue(peak(image,dx,dy)<.02f)}
  }
  ui.runOnIdle{layers=initial.copy(twinkle=true);masks=listOf(GlassMask(origin.x+canvasCenter.x+7*scale,origin.y+canvasCenter.y-3*scale,origin.x+canvasCenter.x+17*scale,origin.y+canvasCenter.y+3*scale,0f))}
  val masked=capture();assertTrue("Mask clips the arm while keeping its external core",peak(masked)>.45f);assertTrue(peak(masked,distance,0)<.01f)
  ui.runOnIdle{masks=emptyList();night=true}
  val red=(0 until 12).map{Thread.sleep(180);capture()}.maxBy{peak(it,distance,0)};assertTrue(peak(red,distance,0)>peak(red,distance,0,1)*1.8f);assertTrue(peak(red,distance,0)>peak(red,distance,0,2)*2f)
 }
 @Test fun physicalCanvasProjectsCatalogMarkerAndHonorsIndependentVisibility() {
  val at=Instant.EPOCH;val site=Site(0.0,0.0,0.0,0.0,ZoneId.of("UTC"));val d=SkyDirection(12.0,8.0)
  val stars=StarSky(site,at,mapOf(24436 to d),mapOf("orion" to d))
  val c=SkyCalculation(site,at,d,d,GalacticSky(site,at,emptyList(),emptyList(),emptyList(),d),emptyList(),emptyList(),emptyList(),stars)
  var layers by mutableStateOf(StarLayers(true,false,false,setOf("orion")))
  ui.setContent{BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)){
   val projection=SkyProjection.virtual(constraints.maxWidth,constraints.maxHeight,70.0,0.0,0.0)
   SkyCanvas(c,SkyTarget.SUN,false,Modifier.fillMaxSize().testTag("star_canvas"),cameraProjection=projection,physical=true,layers=SkyLayers(false,false,false),showGuidance=false,starLayers=layers)
  }}
  ui.waitForIdle()
  val on=ui.onNodeWithTag("star_canvas").captureToImage();val map=on.toPixelMap()
  val focal=on.width/(2*tan(Math.toRadians(35.0)))
  val x=(on.width/2+focal*tan(Math.toRadians(12.0))).roundToInt()
  val y=(on.height/2-focal*tan(Math.toRadians(8.0))/cos(Math.toRadians(12.0))).roundToInt()
  assertTrue(map[x,y].red>.75f)
  ui.runOnIdle{layers=layers.copy(masterVisible=false)}
  val masterOff=ui.onNodeWithTag("star_canvas").captureToImage().toPixelMap()
  assertTrue(masterOff[x,y].red<.05f)
  ui.runOnIdle{layers=layers.copy(masterVisible=true,twinkle=true)}
  val brightness=(0..8).map{Thread.sleep(180);ui.onNodeWithTag("star_canvas").captureToImage().toPixelMap()[x,y].red}
  assertTrue("Physical overlay must twinkle at its unchanged anchor",brightness.max()-brightness.min()>.06f)
  ui.runOnIdle{layers=layers.copy(stars=false)}
  val off=ui.onNodeWithTag("star_canvas").captureToImage().toPixelMap()
  assertTrue(off[x,y].red<.05f)
 }
}
