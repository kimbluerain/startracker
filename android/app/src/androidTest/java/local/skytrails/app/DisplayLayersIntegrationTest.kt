package local.skytrails.app

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import androidx.test.platform.app.InstrumentationRegistry
import local.skytrails.app.location.ManualLocationInput
import local.skytrails.app.ui.*
import local.skytrails.app.ui.renderer.CameraRuntime
import local.skytrails.app.ui.sky.VirtualSkyScene
import local.skytrails.app.ui.sky.StarTwinkleFrames
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant

/** One local native preview; no device permissions, sensor movement or private location. */
class DisplayLayersIntegrationTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 @Before fun setup(){ui.mainClock.autoAdvance=false;ui.runOnUiThread{ui.activity.enableEdgeToEdge()}}
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
 private fun settle(){repeat(7){frame();Thread.sleep(60)}}
 @Test fun shortHeightKeepsFixedCategoriesAndEveryControlReachable() {
  val runtime=CameraRuntime();var state by mutableStateOf(AppState(observationCategory=DisplayLayer.STARS,virtualSky=true,live=false))
  var dateRequests=0
  content{SkyTheme(AppThemeMode.DARK){Box(Modifier.fillMaxWidth().height(393.dp).testTag("short_panel_host")) {
   BottomObservationPanel(state,null,runtime,{state=it(state)},{},{},{dateRequests++},393.dp*.49f,rememberScrollState(),Modifier.align(Alignment.BottomCenter).fillMaxWidth())
  }}}
  frame()
  val panel=ui.onNodeWithTag("observation_controls").fetchSemanticsNode().boundsInRoot
  val host=ui.onNodeWithTag("short_panel_host").fetchSemanticsNode().boundsInRoot
  assertTrue(panel.height<=host.height*.5f)
  tap("star_object_crux");frame();assertFalse("crux" in state.starLayers.enabled)
  tap("star_twinkle");frame();assertTrue(state.starLayers.twinkle)
  tap("edit_datetime");frame();assertEquals(1,dateRequests)
  scrollTo("time_slider").assertIsDisplayed()
  ui.onNodeWithTag("hide_controls").assertIsDisplayed();DisplayLayer.entries.forEach{ui.onNodeWithTag("category_${it.name}").assertIsDisplayed()}
  tap("layers_all_off");frame();assertTrue(DisplayLayer.entries.none(state::displays))
 }
 @Test fun unifiedBottomPanelPreservesDetailsAndProducesActualAppPreview() {
  status("preview vm before")
  lateinit var model:SkyViewModel
  ui.runOnUiThread{model=ViewModelProvider(ui.activity)[SkyViewModel::class.java]}
  status("preview vm after; setContent before")
  content{SkyApp(model,{},{},{})}
  pump{model.settingsReady.value};status("preview settings ready")
  val saved=model.state.value;val at=Instant.parse("2026-10-26T20:00:00Z")
  val place=ManualLocationInput("杭州 · 公开示例","30.2741","120.1551","Asia/Shanghai").selection()
  try {
   ui.runOnIdle{model.select(place);model.change{it.copy(theme=AppThemeMode.DARK,reduceTransparency=false,virtualSky=true,skyFollow=false,live=false,instant=at,controlsHidden=false,layers=SkyLayers(),starLayers=StarLayers()).focus(SkyTarget.MOON)}}
   pump{model.calculation.value?.at==at};settle();status("preview initial frame settled")
   ui.onNodeWithTag("open_star_layers").assertDoesNotExist();ui.onNodeWithTag("star_layers_overlay").assertDoesNotExist()
   val bounds=DisplayLayer.entries.map{ui.onNodeWithTag("category_${it.name}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot}
   assertTrue(bounds.zipWithNext().all{(a,b)->a.right<=b.left&&kotlin.math.abs(a.top-b.top)<1f})
   assertTrue(bounds.maxOf{it.width}-bounds.minOf{it.width}<2f)
   ui.onNodeWithTag("category_MOON").assertIsSelected();ui.onNodeWithTag("display_layer_MOON").assertIsOn()
   capture("01-moon-bottom-panel")
   val before=model.state.value
   ui.onNodeWithTag("category_STARS").performClick();frame()
   val after=model.state.value
   assertFalse("The real-time ticker must not move backwards",after.realNow.isBefore(before.realNow))
   assertEquals(before.copy(observationCategory=DisplayLayer.STARS,realNow=after.realNow),after)
   settle();capture("02-stars-inline-list")
   tap("star_twinkle");frame();tap("star_layer_names");settle()
   assertTrue(model.state.value.starLayers.twinkle);assertFalse(model.state.value.starLayers.names)
   capture("03-stars-display-options")
   tap("star_object_crux");settle();assertFalse("crux" in model.state.value.starLayers.enabled)
   ui.onNodeWithTag("star_info_crux").assertIsDisplayed();capture("04-last-constellation")
   val details=model.state.value.starLayers
   val scroll=ui.onNodeWithTag("observation_detail_scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
   ui.onNodeWithTag("hide_controls").performClick();settle()
   ui.onNodeWithTag("observation_controls").assertDoesNotExist();ui.onNodeWithTag("bottom_navigation").assertDoesNotExist();ui.onNodeWithTag("time_slider").assertDoesNotExist();capture("05-collapsed")
   ui.onNodeWithTag("restore_controls").performClick();settle()
   ui.onNodeWithTag("category_STARS").assertIsSelected();ui.onNodeWithTag("star_object_crux").assertIsDisplayed()
   assertEquals(details,model.state.value.starLayers)
   assertEquals(scroll,ui.onNodeWithTag("observation_detail_scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(),1f)
   tap("layers_all_off");frame()
   assertTrue(DisplayLayer.entries.none(model.state.value::displays));assertEquals(details.enabled,model.state.value.starLayers.enabled)
   assertTrue(model.state.value.starLayers.twinkle);assertFalse(model.state.value.starLayers.names)
   ui.onNodeWithTag("category_GALAXY").performClick();frame();ui.onNodeWithTag("display_layer_GALAXY").performClick();settle()
   assertTrue(model.state.value.layers.galaxy);assertFalse(model.state.value.layers.sun);assertFalse(model.state.value.layers.moon);assertFalse(model.state.value.starLayers.masterVisible)
   capture("06-galaxy-alone")
   tap("layers_all_on");frame()
   assertTrue(DisplayLayer.entries.all(model.state.value::displays));assertEquals(local.skytrails.app.core.StarCatalog.ids,model.state.value.starLayers.enabled)
   assertFalse(model.state.value.starLayers.names);assertTrue(model.state.value.starLayers.twinkle)
   ui.onNodeWithTag("category_STARS").performClick();frame();tap("star_focus_dipper");settle()
   ui.onNodeWithTag("hide_controls").performClick();settle();capture("07-twinkle-immersive")
   if(InstrumentationRegistry.getArguments().getString("recordTwinkle")=="true") {
    InstrumentationRegistry.getInstrumentation().sendStatus(2,android.os.Bundle().apply{putString("stream","TWINKLE_RECORD_START\n")})
    repeat(200){frame();Thread.sleep(50)}
   }
   ui.onNodeWithTag("restore_controls").performClick();frame()
   ui.onNodeWithTag("category_MOON").performClick();frame()
   ui.runOnIdle{model.change{it.copy(theme=AppThemeMode.NIGHT,reduceTransparency=true)}};settle();capture("08-night-opaque-panel")
  }finally{ui.runOnIdle{model.changeSettings{it.copy(theme=saved.theme,reduceTransparency=saved.reduceTransparency,layers=saved.layers,starLayers=saved.starLayers)}};frame()}
 }
 @Test fun actualCrossSpikePreviewAndRecording() {
  lateinit var model:SkyViewModel
  ui.runOnUiThread{model=ViewModelProvider(ui.activity)[SkyViewModel::class.java]}
  content{SkyApp(model,{},{},{})};pump{model.settingsReady.value}
  val saved=model.state.value;val at=Instant.parse("2026-10-26T20:00:00Z")
  val place=ManualLocationInput("杭州 · 公开示例","30.2741","120.1551","Asia/Shanghai").selection()
  val i=InstrumentationRegistry.getInstrumentation();val dir=File(i.targetContext.getExternalFilesDir(null),"v8-preview").apply{mkdirs()}
  fun image(name:String) {settle();val bitmap=i.uiAutomation.takeScreenshot()?:error("Missing actual app frame")
   File(dir,"$name.png").outputStream().use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()}
  try {
   ui.runOnIdle{model.select(place);model.change{it.copy(theme=AppThemeMode.DARK,reduceTransparency=false,virtualSky=true,skyFollow=false,live=false,instant=at,controlsHidden=false,layers=SkyLayers(false,false,false),starLayers=StarLayers(true,false,true,setOf("orion"))).focusConstellation("orion")}}
   pump{model.calculation.value?.at==at};settle()
   scrollTo("star_twinkle").assertIsOff();image("01-bottom-spike-switch")
   ui.onNodeWithTag("hide_controls").performClick();image("02-static-stars")
   ui.onNodeWithTag("restore_controls").performClick();frame();tap("star_twinkle");frame()
   assertTrue(model.state.value.starLayers.twinkle)
   ui.onNodeWithTag("hide_controls").performClick();image("03-cross-spikes")
   if(InstrumentationRegistry.getArguments().getString("recordTwinkle")=="true") {
    i.sendStatus(2,android.os.Bundle().apply{putString("stream","SPIKES_RECORD_START\n")})
    val until=System.nanoTime()+12_000_000_000L
    while(System.nanoTime()<until){frame();Thread.sleep(50)}
   }
   ui.runOnIdle{model.change{it.copy(theme=AppThemeMode.NIGHT)}};image("04-night-spikes")
  }finally{ui.runOnIdle{model.changeSettings{it.copy(theme=saved.theme,reduceTransparency=saved.reduceTransparency,layers=saved.layers,starLayers=saved.starLayers)}};frame()}
 }
 @Test fun startupDoesNotAutoSwitchRenderersWhenLocationArrives() {
  lateinit var model:SkyViewModel
  ui.runOnUiThread{model=ViewModelProvider(ui.activity)[SkyViewModel::class.java]}
  content{SkyApp(model,{},{},{})};pump{model.settingsReady.value}
  ui.runOnIdle{model.change{it.copy(cameraPermission=true,locationPermission=true)}}
  pump{ui.onAllNodesWithTag("virtual_sky_gl").fetchSemanticsNodes().size==1}
  ui.onNodeWithTag("camera_host").assertDoesNotExist()
  val place=ManualLocationInput("公开现场示例","30.2741","120.1551","Asia/Shanghai",confirmedOnSite=true).selection()
  ui.runOnIdle{model.select(place)};pump{model.state.value.location==place};settle()
  ui.onNodeWithTag("camera_host").assertDoesNotExist()
  ui.onNodeWithTag("virtual_sky_gl").assertExists();assertTrue(model.state.value.virtualSky)
  ui.onNodeWithTag("mode_toggle").performClick();frame();ui.onNodeWithTag("camera_host").assertExists()
 }
 @Test fun singleModeIconActualAppPreview() {
  lateinit var model:SkyViewModel
  ui.runOnUiThread{model=ViewModelProvider(ui.activity)[SkyViewModel::class.java]}
  content{SkyApp(model,{},{},{})};pump{model.settingsReady.value}
  val saved=model.state.value;val at=Instant.parse("2026-10-26T20:00:00Z")
  val place=ManualLocationInput("杭州 · 公开示例","30.2741","120.1551","Asia/Shanghai",confirmedOnSite=true).selection()
  val i=InstrumentationRegistry.getInstrumentation();val dir=File(i.targetContext.getExternalFilesDir(null),"v9-preview").apply{mkdirs()}
  fun image(name:String) {settle();val bitmap=i.uiAutomation.takeScreenshot()?:error("Missing actual app frame")
   File(dir,"$name.png").outputStream().use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()}
  try {
   ui.runOnIdle{model.select(place);model.change{it.copy(theme=AppThemeMode.DARK,reduceTransparency=false,virtualSky=true,skyFollow=false,live=false,instant=at,cameraPermission=true,controlsHidden=false,layers=SkyLayers(false,false,false),starLayers=StarLayers(true,true,true,setOf("orion"),twinkle=true)).focusConstellation("orion")}}
   pump{model.calculation.value?.at==at};settle()
   ui.onAllNodesWithTag("mode_toggle").assertCountEquals(1)
   ui.onNodeWithTag("mode_toggle").assertContentDescriptionEquals("切换到实景 AR")
   image("01-sky-panel")
   ui.onNodeWithTag("hide_controls").performClick();image("02-sky-icon")
   ui.onNodeWithTag("mode_toggle").performClick();frame()
   ui.onNodeWithTag("camera_host").assertExists()
   ui.onNodeWithTag("mode_toggle").assertContentDescriptionEquals("切换到星空");image("03-ar-icon")
   ui.onNodeWithTag("mode_toggle").performClick();frame()
   ui.onNodeWithTag("camera_host").assertDoesNotExist();ui.onNodeWithTag("virtual_sky_gl").assertExists()
   assertTrue(model.state.value.controlsHidden);assertTrue(model.state.value.starLayers.twinkle)
   ui.runOnIdle{model.change{it.copy(theme=AppThemeMode.NIGHT,reduceTransparency=true)}};image("04-night-icon")
  }finally{ui.runOnIdle{model.changeSettings{it.copy(theme=saved.theme,reduceTransparency=saved.reduceTransparency,layers=saved.layers,starLayers=saved.starLayers)}};frame()}
 }
 @Test fun zoomAndTwinkleActualAppPreview() {
  lateinit var model:SkyViewModel
  ui.runOnUiThread{model=ViewModelProvider(ui.activity)[SkyViewModel::class.java]}
  content{SkyApp(model,{},{},{})};pump{model.settingsReady.value}
  val saved=model.state.value;val at=Instant.parse("2026-10-26T20:00:00Z")
  val place=ManualLocationInput("杭州 · 公开示例","30.2741","120.1551","Asia/Shanghai",confirmedOnSite=true).selection()
  val i=InstrumentationRegistry.getInstrumentation();val dir=File(i.targetContext.getExternalFilesDir(null),"v10-preview").apply{mkdirs()}
  fun image(name:String){settle();val bitmap=i.uiAutomation.takeScreenshot()?:error("Missing app frame");File(dir,"$name.png").outputStream().use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()}
  try {
   ui.runOnIdle{model.select(place);model.change{it.copy(theme=AppThemeMode.DARK,reduceTransparency=false,virtualSky=true,skyFollow=false,live=false,instant=at,controlsHidden=false,layers=SkyLayers(false,false,false),starLayers=StarLayers(true,true,true,setOf("orion"),twinkle=true)).focusConstellation("orion")}}
   pump{model.calculation.value?.at==at};settle()
   ui.onNodeWithTag("star_twinkle").assertIsDisplayed().assertIsOn();image("01-follow-and-twinkle")
   ui.onNodeWithTag("hide_controls").performClick();image("02-native-detail")
   ui.onNodeWithTag("virtual_sky_gl").performTouchInput {
    val y=height*.44f;down(0,Offset(width*.35f,y));down(1,Offset(width*.65f,y))
    for(n in 1..10){moveTo(0,Offset(width*(.35f-.15f*n/10),y));moveTo(1,Offset(width*(.65f+.15f*n/10),y))};up(0);up(1)
   };image("03-pinched-detail")
   if(InstrumentationRegistry.getArguments().getString("recordTwinkle")=="true") {
    i.sendStatus(2,android.os.Bundle().apply{putString("stream","V10_RECORD_START\n")})
    val until=System.nanoTime()+10_000_000_000L
    while(System.nanoTime()<until){frame();Thread.sleep(50)}
   }
   ui.onNodeWithTag("sky_follow").performClick();frame();assertTrue(model.state.value.skyFollow);image("04-follow-active")
  }finally{ui.runOnIdle{model.changeSettings{it.copy(theme=saved.theme,reduceTransparency=saved.reduceTransparency,layers=saved.layers,starLayers=saved.starLayers)}};frame()}
 }
 @Test fun diagnoseTwinkleCadenceWithoutAdvancingComposeClock() {
  val runtime=CameraRuntime();var state by mutableStateOf(AppState(virtualSky=true,skyFollow=false,live=false))
  val ticks=java.util.concurrent.atomic.AtomicInteger();var owner:androidx.lifecycle.LifecycleOwner?=null
  content {
   val current=androidx.lifecycle.compose.LocalLifecycleOwner.current
   SideEffect{owner=current}
   StarTwinkleFrames(state.starLayers.masterVisible&&state.starLayers.twinkle){ticks.incrementAndGet()}
   VirtualSkyScene(state,null,runtime,null,{state=it(state)},Modifier.fillMaxSize())
  }
  pump{runtime.skyView?.presented?.get()!=null};settle();val view=runtime.skyView!!
  val start=System.nanoTime()
  fun sample(label:String) {
   status("$label wallMs=${(System.nanoTime()-start)/1_000_000} frames=${view.frames} ticks=${ticks.get()} lifecycle=${owner?.lifecycle?.currentState} pendingTwinkle=${view.pending.get()?.starLayers?.twinkle} presentedTwinkle=${view.presented.get()?.starLayers?.twinkle} pendingMaster=${view.pending.get()?.starLayers?.masterVisible} presentedMaster=${view.presented.get()?.starLayers?.masterVisible} error=${view.error} surface=${view.holder.surface.isValid} size=${view.width}x${view.height}")
  }
  sample("before-enable")
  ui.runOnIdle{state=state.copy(starLayers=state.starLayers.copy(twinkle=true))};frame();sample("enabled")
  repeat(8){Thread.sleep(250);sample("wall-only-$it")}
  ui.runOnUiThread{view.requestRender()};Thread.sleep(500);sample("explicit-render")
  repeat(3){frame();Thread.sleep(100);sample("advanced-compose-$it")}
  ui.runOnIdle{state=state.copy(starLayers=state.starLayers.copy(masterVisible=false))};frame();sample("master-off")
  Thread.sleep(500);sample("master-off-later")
  assertTrue("Native pulse must run at least once",ticks.get()>0)
 }
 @Test fun twinkleRenderingStopsWhenHiddenDisabledBackgroundedOrDisposed() {
  val runtime=CameraRuntime();var state by mutableStateOf(AppState(virtualSky=true,skyFollow=false,live=false))
  var observe by mutableStateOf(true)
  content{if(observe)VirtualSkyScene(state,null,runtime,null,{state=it(state)},Modifier.fillMaxSize())}
  pump{runtime.skyView?.presented?.get()!=null};settle();val view=runtime.skyView!!
  fun presented(label:String) {
   val snapshot=view.pending.get();val started=System.nanoTime()
   ui.waitUntil(5000){view.presented.get()===snapshot}
   status("$label presented after ${(System.nanoTime()-started)/1_000_000}ms frames=${view.frames}")
  }
  fun growing(label:String,minimum:Int) {
   presented(label);val n=view.frames;val started=System.nanoTime()
   ui.waitUntil(5000){view.frames>n+minimum}
   status("$label grew $n -> ${view.frames} in ${(System.nanoTime()-started)/1_000_000}ms")
   assertTrue("$label must keep rendering",view.frames>n+minimum)
  }
  fun stable(label:String,awaitFrame:Boolean=true) {
   if(awaitFrame)presented(label)
   Thread.sleep(120);val n=view.frames;Thread.sleep(350)
   status("$label static $n -> ${view.frames}")
   assertEquals("$label must not schedule frames",n,view.frames)
  }
  stable("default-off")
  ui.runOnIdle{state=state.copy(starLayers=state.starLayers.copy(twinkle=true))};frame()
  growing("enabled",2)
  assertSame("Visual pulses do not replace astronomical snapshots",view.pending.get(),view.presented.get())
  ui.runOnIdle{state=state.copy(starLayers=state.starLayers.copy(masterVisible=false))};frame();stable("master-off")
  ui.runOnIdle{state=state.copy(starLayers=state.starLayers.copy(masterVisible=true))};frame()
  growing("master-restored",1)
  ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED);stable("background",awaitFrame=false)
  ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);frame()
  growing("foreground-restored",1);assertSame(view,runtime.skyView)
  ui.runOnIdle{state=state.copy(starLayers=state.starLayers.copy(twinkle=false))};frame();stable("effect-off")
  ui.runOnIdle{state=state.copy(starLayers=state.starLayers.copy(twinkle=true))};frame()
  ui.runOnIdle{observe=false};frame();assertNull(runtime.skyView);stable("disposed",awaitFrame=false)
 }
 private fun capture(name:String) {
  val i=InstrumentationRegistry.getInstrumentation();val image=i.uiAutomation.takeScreenshot()?:error("Missing actual window")
  val dir=File(i.targetContext.getExternalFilesDir(null),"v7-preview").apply{mkdirs()}
  File(dir,"$name.png").outputStream().use{assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};image.recycle()
 }
}
