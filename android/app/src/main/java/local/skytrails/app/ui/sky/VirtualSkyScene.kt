package local.skytrails.app.ui.sky

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import local.skytrails.app.ar.PoseFrame
import local.skytrails.app.core.*
import local.skytrails.app.ui.*
import local.skytrails.app.ui.renderer.*
import java.time.Instant

@Composable fun VirtualSkyScene(s:AppState,c:SkyCalculation?,runtime:CameraRuntime,pose:PoseFrame?,change:((AppState)->AppState)->Unit,modifier:Modifier=Modifier,hudVisible:Boolean=true) {
 val context=LocalContext.current;val owner=LocalLifecycleOwner.current
 val view=remember{SkyGlassView(context)}
 val night=s.theme==AppThemeMode.NIGHT
 StarTwinkleFrames(s.starLayers.masterVisible&&s.starLayers.twinkle){view.requestRender()}
 var manual by remember{mutableStateOf(runtime.manualSkyBasis?:SkyViewBasis.from(SkyProjection.virtual(100,100,70.0,235.0,30.0))!!)}
 var fieldOfView by remember{mutableDoubleStateOf(runtime.skyFieldOfView)}
 val canFollow=s.skyFollow&&pose!=null
 LaunchedEffect(canFollow){if(!canFollow&&s.skyFollow)view.presented.get()?.projection?.let{SkyViewBasis.from(it)?.let{basis->manual=basis}}}
 val updatedChange by rememberUpdatedState(change)
 val directions=remember(c?.site,c?.at) {
  c?.let{val geo=MilkyWayGeometry();val at=it.at
   listOf(GalacticCoordinate(0.0,0.0),GalacticCoordinate(90.0,0.0),GalacticCoordinate(0.0,90.0)).map{p->geo.direction(p,it.site,at)}}
 }
 val intent=s.skyFocusRequest?:s.focusRequest.takeIf{it>0}?.let{SkyFocusRequest(it,s.target,s.location?.site,s.instant)}
 LaunchedEffect(intent,c){runtime.skyFocus.consume(intent,c)?.let{manual=it}}
 DisposableEffect(view,owner) {
  runtime.skyView=view
  val observer=object:DefaultLifecycleObserver{override fun onStart(owner:LifecycleOwner){view.onResume()};override fun onStop(owner:LifecycleOwner){view.onPause()}}
  owner.lifecycle.addObserver(observer)
  onDispose{owner.lifecycle.removeObserver(observer);view.onPause();if(runtime.skyView===view)runtime.skyView=null}
 }
 BoxWithConstraints(modifier) {
  val width=constraints.maxWidth;val height=constraints.maxHeight
  if(width<=0||height<=0)return@BoxWithConstraints
  val offset=if(canFollow)runtime.skyOffset(runtime.displayDegrees,pose.site)else null
  val projection=remember(width,height,canFollow,if(canFollow)pose else null,runtime.displayDegrees,offset,manual,fieldOfView) {
   if(canFollow)SkyProjection.sensor(width,height,fieldOfView,pose,runtime.displayDegrees,offset!!) else SkyProjection.manual(width,height,fieldOfView,manual)
  }
  val basis=remember(projection,directions){directions?.let{GalacticCameraBasis.from(projection,it)}?:GalacticCameraBasis.UNLOCATED}
  val frame=remember(projection,basis,c,s.layers,night,s.starLayers,s.starFocusId,fieldOfView){SkyFrameSnapshot(projection,basis,if(c!=null)projection.toCamera(SkyDirection(0.0,90.0))else null,s.layers.galaxy,night,c,s.layers,s.starLayers,s.starFocusId,horizontalFov=fieldOfView)}
  SideEffect{runtime.manualSkyBasis=manual;runtime.skyFieldOfView=fieldOfView;view.blurEnabled=!s.reduceTransparency&&s.theme!=AppThemeMode.OUTDOOR;view.submit(frame)}
  val latestProjection by rememberUpdatedState(projection)
  AndroidView(factory={view},modifier=Modifier.fillMaxSize().testTag("virtual_sky_gl").onGloballyPositioned{val b=it.boundsInWindow();runtime.originX=b.left;runtime.originY=b.top;runtime.masks()})
  // All precise sky geometry/labels render in SkyPipeline's GL output, before the same swap.
  Box(Modifier.fillMaxSize().pointerInput(Unit){
   awaitEachGesture {
    awaitFirstDown(requireUnconsumed=false)
    var hadMultiplePointers=false;var dragging=false;var travel=Offset.Zero
    do {
     val event=awaitPointerEvent()
     val count=event.changes.count{it.pressed}
     if(event.changes.any{it.isConsumed})break
     if(count>=2) {
      hadMultiplePointers=true
      val zoom=event.calculateZoom()
      fieldOfView=SkyZoom.scaled(fieldOfView,zoom.toDouble())
      event.changes.forEach{it.consume()}
     } else if(!hadMultiplePointers&&count==1) {
      val pan=event.calculatePan();travel+=pan
      if(!dragging&&travel.getDistance()>viewConfiguration.touchSlop) {
       dragging=true;manual=SkyViewBasis.from(view.presented.get()?.projection?:latestProjection)?:manual
       updatedChange{it.copy(skyFollow=false)}
      }
      if(dragging) {
       val degreesPerPixel=fieldOfView/size.width.coerceAtLeast(1)
       manual=manual.dragged(pan.x*degreesPerPixel,-pan.y*degreesPerPixel)
       event.changes.forEach{it.consume()}
      }
     }
    }while(event.changes.any{it.pressed})
   }
  })
  if(hudVisible) {
   val focus=c?.let{when(s.target){SkyTarget.SUN->it.sun;SkyTarget.MOON->it.moon;SkyTarget.GALAXY->it.galaxy.center}}
   if(s.starFocusId==null)FocusedTargetGuidance(s.target,s.layers,focus,projection,canFollow,night,Modifier.align(Alignment.Center))
   else ConstellationGuidance(s.starFocusId,s.starLayers,c?.stars,projection,canFollow,night,Modifier.align(Alignment.Center))
  }
 }
}
