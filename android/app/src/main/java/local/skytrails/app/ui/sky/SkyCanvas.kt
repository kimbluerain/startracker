package local.skytrails.app.ui.sky

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import local.skytrails.app.ui.renderer.GlassMask
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.*
import local.skytrails.app.core.*
import local.skytrails.app.ui.*
import kotlin.math.*

@Composable fun SkyCanvas(calculation:SkyCalculation?,target:SkyTarget,night:Boolean,modifier:Modifier=Modifier,
 cameraProjection:SkyProjection?=null,physical:Boolean=false,exclusions:List<GlassMask> = emptyList(),layers:SkyLayers=SkyLayers(),focusRequest:Long=0,guidancePhysical:Boolean=physical,showGuidance:Boolean=true,starLayers:StarLayers=StarLayers(),starFocusId:String?=null) {
 var azimuth by remember {mutableFloatStateOf(235f)};var altitude by remember {mutableFloatStateOf(30f)}
 val site=calculation?.site
 LaunchedEffect(site,target,focusRequest) {
  calculation?.let {c->val focus=when(target){SkyTarget.SUN->c.sun;SkyTarget.MOON->c.moon;SkyTarget.GALAXY->c.galaxy.center};azimuth=focus.azimuthDeg.toFloat();altitude=focus.altitudeDeg.toFloat().coerceIn(12f,65f)}
 }
 var twinkleSeconds by remember{mutableFloatStateOf(0f)}
 val measure=rememberTextMeasurer();var origin by remember{mutableStateOf(Offset.Zero)}
 val fov=70.0
 BoxWithConstraints(modifier) {
  val width=constraints.maxWidth;val height=constraints.maxHeight
  val projection=if(physical)cameraProjection else if(width>0&&height>0)SkyProjection.virtual(width,height,fov,azimuth.toDouble(),altitude.toDouble())else null
  val starDrawing=remember(calculation?.stars,starLayers,projection){projection?.let{StarOverlay.render(calculation?.stars,starLayers,it)}}
  StarTwinkleFrames(starLayers.twinkle&&starDrawing?.stars?.isNotEmpty()==true){twinkleSeconds=it}
  if(!physical)SkyBackdrop(site,calculation?.at,Modifier.fillMaxSize(),azimuth,altitude,layers.galaxy,night,fov,exposure=.70f,starsVisible=starLayers.masterVisible)
  Canvas(Modifier.fillMaxSize().onGloballyPositioned{val b=it.boundsInWindow();origin=Offset(b.left,b.top)}.pointerInput(physical){if(!physical)detectDragGestures { change,drag->change.consume();azimuth=(azimuth-drag.x*.1f+360)%360;altitude=(altitude+drag.y*.1f).coerceIn(-80f,80f) }}) {
   if(size.width<=0||size.height<=0)return@Canvas
   if(projection==null)return@Canvas
   var visible=Path().apply{addRect(Rect(0f,0f,size.width,size.height))}
   exclusions.forEach{m->val hole=Path().apply{addRoundRect(RoundRect(Rect(m.left-origin.x,m.top-origin.y,m.right-origin.x,m.bottom-origin.y),CornerRadius(m.radius)))};visible=Path.combine(PathOperation.Difference,visible,hole)}
   clipPath(visible) {
    val ink=if(night)NightRed else MoonSilver
    fun label(text:String,p:Offset,color:Color=ink){val layout=measure.measure(text,TextStyle(color=color,fontSize=12.sp,shadow=Shadow(Color.Black,Offset(0f,1f),5f)));drawText(layout,topLeft=Offset(p.x.coerceIn(2f,max(2f,size.width-layout.size.width-2f)),p.y.coerceIn(2f,max(2f,size.height-layout.size.height-2f))))}
    val horizon=(0..360 step 2).map{SkyDirection(it.toDouble(),0.0)}
    GalacticOverlay.path(horizon,projection).forEach {drawLine(ink.copy(alpha=.25f),Offset(it.start.x.toFloat(),it.start.y.toFloat()),Offset(it.end.x.toFloat(),it.end.y.toFloat()),1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(),7.dp.toPx())))}
    listOf(0.0 to "北 N",90.0 to "东 E",180.0 to "南 S",270.0 to "西 W").forEach {(a,t)->val indicator=GalacticOverlay.target(SkyDirection(a,0.0),projection);if(indicator is TargetIndicator.Visible)label(t,Offset(indicator.pixel.x.toFloat()+5.dp.toPx(),indicator.pixel.y.toFloat()+8.dp.toPx()),ink.copy(alpha=.7f))}
    if(calculation==null)return@clipPath
    val stars=starDrawing?:return@clipPath
    stars.lines.forEach{stroke->val selected=stroke.id==starFocusId;val color=if(night)NightRed else if(selected)SunGold else MoonSilver
     drawLine(color.copy(alpha=if(selected).54f else .28f),Offset(stroke.segment.start.x.toFloat(),stroke.segment.start.y.toFloat()),Offset(stroke.segment.end.x.toFloat(),stroke.segment.end.y.toFloat()),if(selected)1.05.dp.toPx()else .75.dp.toPx())}
    stars.stars.forEach{star->
     val p=Offset(star.pixel.x.toFloat(),star.pixel.y.toFloat());val radius=(3.4-star.magnitude*.55).coerceIn(1.15,3.5).toFloat().dp.toPx()
     val brightness=StarTwinkle.brightness(star.hip,twinkleSeconds,starLayers.twinkle)
     StarSpikes.profile(star.magnitude,starLayers.twinkle)?.let{profile->drawStarSpikes(p,profile,ink,profile.strength*StarSpikes.intensity(star.hip,twinkleSeconds,star.below))}
     drawCircle(ink.copy(alpha=.05f*brightness),radius+4.dp.toPx(),p);drawCircle(ink.copy(alpha=(if(star.below).55f else .92f)*brightness),radius,p)
    }
    stars.names.forEach{label(it.text,Offset(it.pixel.x.toFloat()+12.dp.toPx(),it.pixel.y.toFloat()+15.dp.toPx()),(if(night)NightRed else if(it.id==starFocusId)SunGold else MoonSilver).copy(alpha=if(it.id==starFocusId).95f else .6f))}
    if(layers.moon)moonEndpointLabels(calculation,projection).forEach{endpoint->val p=Offset(endpoint.pixel.x.toFloat(),endpoint.pixel.y.toFloat());drawCircle(ink.copy(alpha=.75f),3.5.dp.toPx(),p,style=Stroke(.8.dp.toPx()));val textAt=moonEndpointLabelPosition(endpoint,GalacticOverlay.target(calculation.moon,projection),density);label(endpoint.text,Offset(textAt.x.toFloat(),textAt.y.toFloat()),ink.copy(alpha=.78f))}
    layers.visibleTargets.forEach {visibleTarget->
     val color=targetColor(visibleTarget,night)
     val paths=when(visibleTarget){SkyTarget.SUN->GalacticOverlay.path(calculation.sunPath,projection);SkyTarget.MOON->GalacticOverlay.path(calculation.moonPath,projection);SkyTarget.GALAXY->GalacticOverlay.render(calculation.galaxy,projection).segments}
     paths.forEach {segment->drawLine(color.copy(alpha=if(segment.line==GalacticLine.BAND_EDGE).14f else if(visibleTarget==SkyTarget.GALAXY).45f else if(segment.belowHorizon).35f else .7f),Offset(segment.start.x.toFloat(),segment.start.y.toFloat()),Offset(segment.end.x.toFloat(),segment.end.y.toFloat()),if(segment.line==GalacticLine.BAND_EDGE).6.dp.toPx() else 1.dp.toPx(),pathEffect=if(visibleTarget==SkyTarget.MOON)null else if(segment.belowHorizon||visibleTarget==SkyTarget.GALAXY)PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(),6.dp.toPx())) else null)}
     val direction=when(visibleTarget){SkyTarget.SUN->calculation.sun;SkyTarget.MOON->calculation.moon;SkyTarget.GALAXY->calculation.galaxy.center}
     when(val mark=GalacticOverlay.target(direction,projection)) {
      is TargetIndicator.Visible->{
       val p=Offset(mark.pixel.x.toFloat(),mark.pixel.y.toFloat())
       val r=if(visibleTarget==SkyTarget.GALAXY)4.dp.toPx() else 6.dp.toPx()
       drawCircle(Brush.radialGradient(listOf(color.copy(alpha=.28f),Color.Transparent),p,26.dp.toPx()),26.dp.toPx(),p)
       drawCircle(color,r,p);drawCircle(color.copy(alpha=.7f),11.dp.toPx(),p,style=Stroke(.8.dp.toPx()))
       label(if(visibleTarget==SkyTarget.GALAXY)"银心" else targetName(visibleTarget),p+Offset(17.dp.toPx(),(-9).dp.toPx()),color)
      }
      is TargetIndicator.Edge->{val p=Offset(mark.pixel.x.toFloat(),mark.pixel.y.toFloat());val d=Offset(cos(mark.angleRadians).toFloat(),sin(mark.angleRadians).toFloat());val n=Offset(-d.y,d.x);drawLine(color,p-d*12f+n*6f,p,2f);drawLine(color,p-d*12f-n*6f,p,2f)}
      is TargetIndicator.Behind->Unit // One focused-target hint below remains visible when controls are hidden.
      else->Unit
     }
    }
   }
  }
  val focus=calculation?.let{when(target){SkyTarget.SUN->it.sun;SkyTarget.MOON->it.moon;SkyTarget.GALAXY->it.galaxy.center}}
  if(showGuidance) {
   if(starFocusId==null)FocusedTargetGuidance(target,layers,focus,projection,guidancePhysical,night,Modifier.align(Alignment.Center))
   else ConstellationGuidance(starFocusId,starLayers,calculation?.stars,projection,guidancePhysical,night,Modifier.align(Alignment.Center))
  }
 }
}

/** The same fixed optical profile as GLES, clipped by the surrounding physical overlay. */
private fun DrawScope.drawStarSpikes(center:Offset,profile:StarSpikeProfile,color:Color,alpha:Float) {
 val clear=color.copy(alpha=0f);val halo=profile.haloRadius.dp.toPx()
 drawCircle(Brush.radialGradient(listOf(color.copy(alpha=alpha*StarSpikes.HALO_ALPHA),clear),center,halo),halo,center)
 val length=profile.halfLength.dp.toPx()
 for(axis in 0 until 4) {
  val direction=when(axis){0->Offset(1f,0f);1->Offset(0f,1f);2->Offset(-1f,0f);else->Offset(0f,-1f)}
  val tip=center+direction*length
  fun arm(halfWidth:Float,opacity:Float) {
   val normal=Offset(-direction.y,direction.x)*halfWidth.dp.toPx()
   val path=Path().apply{moveTo(center.x+normal.x,center.y+normal.y);lineTo(tip.x,tip.y);lineTo(center.x-normal.x,center.y-normal.y);close()}
   drawPath(path,Brush.linearGradient(listOf(color.copy(alpha=alpha*opacity),clear),center,tip))
  }
  arm(profile.halfWidth*StarSpikes.SOFT_WIDTH_SCALE,StarSpikes.SOFT_ALPHA)
  arm(profile.halfWidth,StarSpikes.CORE_ALPHA)
 }
}
