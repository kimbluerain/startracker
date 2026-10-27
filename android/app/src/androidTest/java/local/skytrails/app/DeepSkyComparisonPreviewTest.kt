package local.skytrails.app

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import local.skytrails.app.location.ManualLocationInput
import local.skytrails.app.ui.*
import local.skytrails.app.ui.renderer.SkyGlassView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant

/** Same public scene and physical pixels on v10 and v11; no private/device location. */
class DeepSkyComparisonPreviewTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 private fun skyView(v:View):SkyGlassView? {
  if(v is SkyGlassView)return v
  if(v is ViewGroup)for(i in 0 until v.childCount)skyView(v.getChildAt(i))?.let{return it}
  return null
 }
 @Test fun sameSkyAtWideAndCloseZoomAndLayerControls() {
  val inst=InstrumentationRegistry.getInstrumentation()
  val label=InstrumentationRegistry.getArguments().getString("comparisonLabel")?:"candidate"
  require(label.matches(Regex("[a-z0-9-]+")))
  val dir=File(inst.targetContext.getExternalFilesDir(null),"v11-preview/$label").apply{mkdirs()}
  ui.mainClock.autoAdvance=false
  lateinit var model:SkyViewModel
  ui.runOnUiThread{ui.activity.enableEdgeToEdge();model=ViewModelProvider(ui.activity)[SkyViewModel::class.java]}
  ui.setContent{SkyApp(model,{},{},{})}
  fun frame(){ui.mainClock.advanceTimeBy(64);ui.waitForIdle()}
  ui.waitUntil(15000){frame();model.settingsReady.value}
  val saved=model.state.value
  val at=Instant.parse("2026-06-15T15:00:00Z")
  val place=ManualLocationInput("杭州 · 公开示例","30.2741","120.1551","Asia/Shanghai",confirmedOnSite=true).selection()
  fun settle() {
   repeat(8){frame();Thread.sleep(80)}
   // Give asynchronous high-detail data enough wall time; v10 takes the same bounded stage.
   repeat(25){frame();Thread.sleep(80)}
  }
  fun image(name:String) {
   settle()
   lateinit var view:SkyGlassView
   ui.runOnUiThread{view=skyView(ui.activity.window.decorView)?:error("Missing native sky")}
   fun metric(name:String):Any?=runCatching{view.javaClass.getMethod(name).invoke(view)}.getOrNull()
   if(metric("getTileIdle")!=null)ui.waitUntil(30000){frame();metric("getTileIdle")==true}
   assertNull(view.error);assertNull(metric("getTileError"))
   val resident=(metric("getTileResidentBytes") as? Long)?:0L
   assertTrue(resident<=48L*1024*1024)
   val presented=view.presented.get()?:error("No presented sky")
   assertEquals(at,presented.calculation?.at)
   val bitmap=inst.uiAutomation.takeScreenshot()?:error("No screenshot")
   try{File(dir,"$name.png").outputStream().use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}}finally{bitmap.recycle()}
   File(dir,"frames.txt").appendText("$name fov=${presented.horizontalFov} frames=${view.frames} pssKiB=${android.os.Debug.getPss()} tileBytes=$resident tileLevel=${metric("getActiveTileLevel")} decodes=${metric("getTileDecodeCount")} pending=${metric("getTilePendingJobs")} fieldCount=${metric("getLoadedFieldStarCount")}\n")
  }
  try {
   ui.runOnIdle{model.select(place);model.change{it.copy(theme=AppThemeMode.DARK,reduceTransparency=false,virtualSky=true,skyFollow=false,live=false,instant=at,controlsHidden=true,layers=SkyLayers(false,false,true),starLayers=StarLayers(false,false,false,emptySet(),masterVisible=true,twinkle=false)).focus(SkyTarget.GALAXY)}}
   ui.waitUntil(15000){frame();model.calculation.value?.at==at}
   image("01-galaxy-70")
   repeat(2){ui.onNodeWithTag("virtual_sky_gl").performTouchInput {
    val y=height*.45f;down(0,Offset(width*.35f,y));down(1,Offset(width*.65f,y))
    for(n in 1..10){moveTo(0,Offset(width*(.35f-.15f*n/10),y));moveTo(1,Offset(width*(.65f+.15f*n/10),y))};up(0);up(1)
   };frame()}
   image("02-galaxy-20")
   ui.runOnUiThread{assertEquals(20.0,skyView(ui.activity.window.decorView)!!.presented.get()!!.horizontalFov,.01)}
   ui.runOnIdle{model.change{it.copy(layers=SkyLayers(false,false,false))}}
   image("03-stars-20-static")
   ui.runOnIdle{model.change{it.copy(starLayers=it.starLayers.copy(twinkle=true))}}
   image("04-stars-20-twinkle")
   ui.runOnIdle{model.change{it.copy(layers=SkyLayers(false,false,true),theme=AppThemeMode.NIGHT,reduceTransparency=true)}}
   image("05-night-20")
   ui.runOnIdle{model.change{it.copy(theme=AppThemeMode.DARK,reduceTransparency=false,controlsHidden=false,observationCategory=DisplayLayer.STARS)}}
   settle()
   ui.onNodeWithTag("star_twinkle").assertIsOn()
   image("06-bottom-controls")
  }finally{ui.runOnIdle{model.changeSettings{it.copy(theme=saved.theme,reduceTransparency=saved.reduceTransparency,layers=saved.layers,starLayers=saved.starLayers)}};frame()}
 }
}
