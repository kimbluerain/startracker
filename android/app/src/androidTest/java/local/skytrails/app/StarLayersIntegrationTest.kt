package local.skytrails.app

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import androidx.test.platform.app.InstrumentationRegistry
import local.skytrails.app.core.*
import local.skytrails.app.core.Target
import local.skytrails.app.location.ManualLocationInput
import local.skytrails.app.ui.*
import local.skytrails.app.ui.renderer.CameraRuntime
import local.skytrails.app.ui.sky.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.*

/** Only public synthetic fixtures, no location permissions or GPS. Root owns device execution. */
class StarLayersIntegrationTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 @Before fun clock(){ui.mainClock.autoAdvance=false;ui.runOnUiThread{ui.activity.enableEdgeToEdge()}}
 private lateinit var scrollScope:CoroutineScope
 private fun content(block:@Composable ()->Unit){ui.setContent{scrollScope=rememberCoroutineScope();block()}}
 private fun frame(){ui.mainClock.advanceTimeBy(64);ui.waitForIdle()}
 // Center the target where possible and finish scrolling before delivering a real tap.
 private fun scrollTo(tag:String):SemanticsNodeInteraction {
  val target=ui.onNodeWithTag(tag)
  repeat(12) {step->
   val node=target.fetchSemanticsNode();val bounds=Rect(node.positionInRoot,node.size.toSize())
   val scroll=ui.onNode(hasScrollAction());val viewport=scroll.fetchSemanticsNode().boundsInRoot
   val range=scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
   val delta=bounds.center.y-viewport.center.y
   val fits=bounds.top>=viewport.top&&bounds.bottom<=viewport.bottom
   val atEnd=(delta<0&&range.value()<=.5f)||(delta>0&&range.value()>=range.maxValue()-.5f)
   val inset=minOf(24f,((viewport.height-bounds.height)/4).coerceAtLeast(0f))
   if((bounds.top>=viewport.top+inset&&bounds.bottom<=viewport.bottom-inset)||(fits&&atEnd))return target.assertIsDisplayed()
   val startingValue=range.value()
   val clamped=delta.coerceIn(-startingValue,range.maxValue()-startingValue)
   status("scroll $tag step=$step requested=$delta clamped=$clamped target=$bounds viewport=$viewport value=$startingValue")
   // ScrollBy launches and returns before its animation ends. Await ScrollByOffset in
   // this composition's clock context: hitting max scroll is not animation completion.
   lateinit var completion:Deferred<Offset>
   val action=scroll.fetchSemanticsNode().config[SemanticsActions.ScrollByOffset]
   ui.runOnUiThread{completion=scrollScope.async{action(Offset(0f,clamped))}}
   var previous=startingValue;var settled=0
   for(frameIndex in 0 until 16) {
    ui.mainClock.advanceTimeBy(64);ui.waitForIdle()
    val current=scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    val currentNode=target.fetchSemanticsNode()
    val expectedTop=bounds.top-(current-startingValue)
    val layoutMatches=kotlin.math.abs(currentNode.positionInRoot.y-expectedTop)<.75f
    settled=if(completion.isCompleted&&kotlin.math.abs(current-previous)<.5f&&layoutMatches)settled+1 else 0;previous=current
    if(settled>=3)break
   }
   if(!completion.isCompleted){completion.cancel();error("ScrollByOffset for $tag did not complete within 16 clock steps")}
   val consumed=runBlocking{completion.await()}
   check(settled>=3){"Completed scroll layout for $tag did not settle"}
   status("scroll $tag completed consumed=$consumed; settled value=$previous full=${Rect(target.fetchSemanticsNode().positionInRoot,target.fetchSemanticsNode().size.toSize())} clipped=${target.fetchSemanticsNode().boundsInRoot}")
  }
  error("Scroll did not reveal $tag after 12 real actions")
 }
 private fun tap(tag:String) {
  val target=scrollTo(tag);val node=target.fetchSemanticsNode();val clipped=node.boundsInRoot
  val root=ui.onRoot().fetchSemanticsNode().boundsInRoot
  check(clipped.width>0&&clipped.height>0){"Empty tap target $tag"}
  val point=Offset(clipped.center.x-root.left,clipped.center.y-root.top)
  status("tap $tag full=${Rect(node.positionInRoot,node.size.toSize())} clipped=$clipped root=$root point=$point")
  if(tag=="star_object_crux") {
   val instrumentation=InstrumentationRegistry.getInstrumentation()
   val bitmap=instrumentation.uiAutomation.takeScreenshot()?:error("No tap evidence")
   val dir=File(instrumentation.targetContext.getExternalFilesDir(null),"v7-tap-probe").apply{mkdirs()}
   File(dir,"${javaClass.simpleName}-crux-before.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  }
  ui.onRoot().performTouchInput {down(point);advanceEventTime(80);up()}
 }
 private fun status(value:String){InstrumentationRegistry.getInstrumentation().sendStatus(2,android.os.Bundle().apply{putString("stream","LAYER_PROBE $value\n")})}
 private fun pump(condition:()->Boolean){ui.waitUntil(15000){ui.mainClock.advanceTimeBy(64);condition()}}
 private val at=Instant.parse("2026-10-26T20:00:00Z")
 private val place=ManualLocationInput("杭州 · 公开示例","30.2741","120.1551","Asia/Shanghai").selection()
 private fun calculation():SkyCalculation {
  val a=AstronomyEngineAdapter();val site=place.site;val day=dayWindow(at.atZone(site.zone).toLocalDate(),site.zone)
  val times=(0..144).map{day.start.plusSeconds(Duration.between(day.start,day.endExclusive).seconds*it/144)}
  return SkyCalculation(site,at,a.position(Target.SUN,site,at,Atmosphere.AIRLESS),a.position(Target.MOON,site,at,Atmosphere.AIRLESS),MilkyWayGeometry().sky(site,at),emptyList(),times.map{a.position(Target.SUN,site,it,Atmosphere.AIRLESS)},times.map{a.position(Target.MOON,site,it,Atmosphere.AIRLESS)},StarCatalog.sky(site,at))
 }
 @Test fun bottomPanelIsBoundedAndCategoriesDoNotMoveTheSky() {
  val runtime=CameraRuntime();val c=calculation();var s by mutableStateOf(AppState(location=place,instant=at,live=false,virtualSky=true,skyFollow=true))
  content{SkyTheme(AppThemeMode.DARK){ObserveScreen(s,c,runtime,{s=it(s)},{},{},{},{},{})}}
  pump{runtime.skyView?.presented?.get()?.calculation===c};frame()
  val renderer=runtime.skyView;val before=s
  for(category in DisplayLayer.entries) {
   ui.onNodeWithTag("category_${category.name}").performClick();frame()
   ui.onNodeWithTag("category_${category.name}").assertIsSelected()
   assertEquals(before.copy(observationCategory=category),s)
   assertSame(renderer,runtime.skyView)
  }
  for(category in DisplayLayer.entries) {
   ui.onNodeWithTag("category_${category.name}").performClick();frame()
   val prior=s
   ui.onNodeWithTag("display_layer_${category.name}").assertIsOn().performClick();frame()
   ui.onNodeWithTag("display_layer_${category.name}").assertIsOff()
   DisplayLayer.entries.forEach {other->assertEquals(if(other==category)false else prior.displays(other),s.displays(other))}
   assertEquals(prior.target,s.target);assertEquals(prior.starFocusId,s.starFocusId);assertEquals(prior.focusRequest,s.focusRequest)
   assertEquals(prior.skyFollow,s.skyFollow);assertEquals(prior.virtualSky,s.virtualSky);assertEquals(prior.skyFocusRequest,s.skyFocusRequest)
  }
  val bounds=ui.onNodeWithTag("observation_controls").fetchSemanticsNode().boundsInRoot
  val root=ui.onRoot().fetchSemanticsNode().boundsInRoot
  assertTrue("Bottom panel exceeds half the view",bounds.height<=root.height*.5f)
  assertTrue("Panel must stay in lower half",bounds.top>=root.height*.35f)
  ui.onNodeWithTag("open_star_layers").assertDoesNotExist();ui.onNodeWithTag("star_layers_overlay").assertDoesNotExist()
  assertTrue(runtime.windowMasks.containsKey("observation"));assertFalse(runtime.windowMasks.containsKey("star_layers_sheet"))
 }
 @Test fun allRepresentationsOffHidesBehindGuidanceAndMarksInformationHidden() {
  val c=calculation();val d=c.stars!!.centers.getValue("orion")
  val runtime=CameraRuntime().apply{manualSkyBasis=SkyViewBasis.from(SkyProjection.virtual(100,100,70.0,(d.azimuthDeg+180)%360,-d.altitudeDeg))}
  var s by mutableStateOf(AppState(location=place,instant=at,live=false,virtualSky=true,skyFollow=false,starFocusId="orion",starLayers=StarLayers(true,false,false,setOf("orion")),observationCategory=DisplayLayer.STARS))
  content{SkyTheme(AppThemeMode.DARK){ObserveScreen(s,c,runtime,{s=it(s)},{},{},{},{},{})}}
  pump{runtime.skyView?.presented?.get()?.calculation===c}
  ui.onNodeWithTag("constellation_guidance").assertExists()
  ui.runOnIdle{s=s.copy(starLayers=s.starLayers.copy(stars=false))};frame()
  ui.onNodeWithTag("constellation_guidance").assertDoesNotExist()
  ui.onNodeWithText("猎户座 · 已隐藏").assertExists()
 }
 @Test fun inlineControlsKeepRendererAndIndependentChoicesReachPresentedFrame() {
  val runtime=CameraRuntime();val c=calculation();var s by mutableStateOf(AppState(location=place,instant=at,live=false,virtualSky=true,skyFollow=false,theme=AppThemeMode.DARK))
  content{SkyTheme(s.theme){ObserveScreen(s,c,runtime,{s=it(s)},{},{},{},{},{})}}
  pump{runtime.skyView?.presented?.get()?.calculation===c}
  val view=runtime.skyView
  ui.onNodeWithTag("category_STARS").performClick();frame()
  ui.onNodeWithTag("observe_header").assertExists();ui.onNodeWithTag("observation_controls").assertExists()
  assertTrue(runtime.windowMasks.containsKey("observation"));assertSame(view,runtime.skyView)
  val solar=s.layers
  tap("star_layer_stars");frame();tap("star_layer_names");frame();tap("star_object_crux");frame()
  pump{view?.presented?.get()?.starLayers==s.starLayers}
  assertFalse(s.starLayers.stars);assertTrue(s.starLayers.lines);assertFalse(s.starLayers.names);assertFalse("crux" in s.starLayers.enabled);assertEquals(solar,s.layers)
  tap("star_focus_orion");frame()
  pump{runtime.skyFocus.consumed==s.focusRequest}
  val desired=view!!.pending.get()!!;pump{view.presented.get()===desired}
  val mark=GalacticOverlay.target(c.stars!!.centers.getValue("orion"),desired.projection) as TargetIndicator.Visible
  assertEquals(desired.projection.width/2.0,mark.pixel.x,.01);assertEquals(desired.projection.height/2.0,mark.pixel.y,.01)
  assertEquals("orion",s.starFocusId);assertEquals(solar,s.layers);assertSame(view,runtime.skyView)
  ui.onNodeWithTag("observation_controls").assertIsDisplayed();assertTrue(runtime.windowMasks.containsKey("observation"))
 }
 @Test fun eachPatternFocusesAndCollapsedPanelRestoresLastObject() {
  val runtime=CameraRuntime();val c=calculation();var s by mutableStateOf(AppState(location=place,instant=at,live=false,virtualSky=true,skyFollow=false,observationCategory=DisplayLayer.STARS))
  content{SkyTheme(AppThemeMode.DARK){ObserveScreen(s,c,runtime,{s=it(s)},{},{},{},{},{})}}
  for(id in listOf("dipper","orion","cassiopeia","crux")) {
   tap("star_focus_$id");frame()
   pump{runtime.skyFocus.consumed==s.focusRequest}
   val view=runtime.skyView!!;val focused=view.pending.get()!!;pump{view.presented.get()===focused}
   val mark=GalacticOverlay.target(c.stars!!.centers.getValue(id),focused.projection) as TargetIndicator.Visible
   assertEquals(focused.projection.width/2.0,mark.pixel.x,.01);assertEquals(focused.projection.height/2.0,mark.pixel.y,.01)
   assertEquals(id,s.starFocusId)
  }
  val before=s;val scroll=ui.onNodeWithTag("observation_detail_scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
  val view=runtime.skyView
  ui.onNodeWithTag("hide_controls").performClick();frame()
  ui.onNodeWithTag("observation_controls").assertDoesNotExist();ui.onNodeWithTag("category_STARS").assertDoesNotExist();ui.onNodeWithTag("restore_controls").assertIsDisplayed()
  ui.onNodeWithTag("restore_controls").performClick();frame()
  assertEquals(before,s);assertSame(view,runtime.skyView);ui.onNodeWithTag("star_focus_crux").assertIsDisplayed()
  assertEquals(scroll,ui.onNodeWithTag("observation_detail_scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(),1f)
 }
 @Test fun actualAppPreviewScreenshots() {
  lateinit var model:SkyViewModel
  ui.runOnUiThread{model=ViewModelProvider(ui.activity)[SkyViewModel::class.java]}
  content{SkyApp(model,{},{},{})};pump{model.settingsReady.value}
  val saved=UserSettings(model.state.value.theme,model.state.value.reduceTransparency,model.state.value.layers,model.state.value.starLayers)
  try {
   ui.runOnIdle{model.select(place);model.change{it.copy(theme=AppThemeMode.DARK,reduceTransparency=false,virtualSky=true,skyFollow=false,live=false,instant=at,layers=SkyLayers(false,false,true),starLayers=StarLayers(),controlsHidden=false)}}
   pump{model.calculation.value?.stars?.at==at}
   ui.runOnIdle{model.change{it.focusConstellation("orion")}};settle();capture("01-observe-orion")
   ui.onNodeWithTag("category_STARS").performClick();settle();ui.onNodeWithTag("bottom_navigation").assertExists();ui.onNodeWithTag("observe_header").assertExists();capture("02-star-details")
   tap("star_focus_dipper");settle();ui.onNodeWithTag("hide_controls").performClick();settle();ui.onNodeWithTag("bottom_navigation").assertDoesNotExist();capture("03-immersive-dipper")
   val midnight=Instant.parse("2026-10-25T16:10:00Z")
   ui.runOnIdle{model.change{it.copy(instant=midnight,live=false,controlsHidden=false,layers=SkyLayers(false,true,false),starLayers=StarLayers(false,false,false,emptySet())).focus(SkyTarget.MOON,midnight)}}
   pump{model.calculation.value?.at==midnight};settle();capture("04-moon-day-endpoints")
  } finally {
   ui.runOnIdle{model.changeSettings{it.copy(theme=saved.theme,reduceTransparency=saved.reduceTransparency,layers=saved.layers,starLayers=saved.starLayers)}};frame()
  }
 }
 private fun settle(){repeat(12){frame();Thread.sleep(60)}}
 private fun capture(name:String) {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val bitmap=instrumentation.uiAutomation.takeScreenshot()?:error("No actual window screenshot")
  val dir=File(instrumentation.targetContext.getExternalFilesDir(null),"v7-star-regression").apply{mkdirs()}
  val file=File(dir,"$name.png");file.outputStream().use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
  instrumentation.sendStatus(2,android.os.Bundle().apply{putString("stream","Actual full-window screenshot: ${file.absolutePath}\n")})
 }
}
