package local.skytrails.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import local.skytrails.app.core.EventKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class PlanPhoto(val source:android.graphics.Bitmap,val night:Boolean,val image:android.graphics.Bitmap)
@Composable private fun PlanLandscape(night:Boolean,modifier:Modifier=Modifier) {
 val assets=LocalContext.current.assets
 val image by produceState<android.graphics.Bitmap?>(null) {value=withContext(Dispatchers.IO){assets.open("sky/plan-dusk.png").use{BitmapFactory.decodeStream(it)}}}
 val prepared by produceState<PlanPhoto?>(null,image,night) {
  value=image?.let{source->withContext(Dispatchers.Default){PlanPhoto(source,night,if(night)nightBackdropBitmap(source)else source)}}
 }
 // Do not briefly show the previous colour photograph when the user enters Night mode.
 val visible=prepared?.takeIf{it.source===image&&it.night==night}?.image
 FrostedBitmapBackdrop(visible,modifier.background(if(night)Color(0xFF100403)else Color(0xFF11151C)),ContentScale.Crop)
}
fun eventName(event:TimedEvent)="${if(event.target==SkyTarget.SUN)"日" else "月"}${if(event.event.kind==EventKind.RISE)"出" else "落"}"
private fun cardinal(azimuth:Double)=listOf("北","东北","东","东南","南","西南","西","西北")[((azimuth+22.5)/45).toInt()%8]
@Composable fun PlanScreen(s:AppState,c:SkyCalculation?,change:((AppState)->AppState)->Unit,onPreview:(Instant)->Unit,onNow:()->Unit,onDate:()->Unit) {
 val theme=pageTheme(s);val night=theme==AppThemeMode.NIGHT;val light=theme==AppThemeMode.LIGHT||theme==AppThemeMode.OUTDOOR
 val opaque=s.reduceTransparency||theme==AppThemeMode.OUTDOOR
 val color=if(night)NightRed else if(light)Ink else MoonSilver
 val zone=s.location?.site?.zone ?: ZoneId.systemDefault();val local=s.instant.atZone(zone)
 val next=c?.events?.firstOrNull{it.event.instant>=s.instant};val featured=next ?: c?.events?.lastOrNull()
 Box(Modifier.fillMaxSize()) {
  PlanLandscape(night,Modifier.fillMaxWidth().fillMaxHeight(.8f))
  val shade=if(night)Color(0xFF100403)else Color(0xFF101216)
  Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(shade.copy(alpha=.35f),shade.copy(alpha=.08f),shade.copy(alpha=.95f),shade))))
  if(light)Box(Modifier.fillMaxSize().background(Color(0xFFF2EFEA).copy(alpha=if(theme==AppThemeMode.OUTDOOR)1f else .87f)))
  PageGlassContent(s) {
   Column(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom=76.dp).verticalScroll(rememberScrollState()).statusBarsPadding().padding(start=22.dp,end=22.dp,top=22.dp,bottom=20.dp)) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
     Text("计划",Modifier.weight(1f),fontSize=38.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-1).sp)
     FrostedSurface(Modifier.size(48.dp),opaque,night,24.dp) {IconButton(onClick=onDate,modifier=Modifier.testTag("plan_date").semantics{contentDescription="选择日期"}){SkyGlyph(SkyIcon.CALENDAR,color=color)}}
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
     TextButton(onClick={onPreview(local.minusDays(1).toInstant())},enabled=local.minusDays(1).year in 2000..2050,contentPadding=PaddingValues(0.dp),modifier=Modifier.width(32.dp).heightIn(min=48.dp).semantics{contentDescription="前一天"}){Text("‹",fontSize=28.sp)}
     Text("${s.location?.name ?: "选择地点"} · ${local.format(DateTimeFormatter.ofPattern("M月d日"))}",Modifier.weight(1f).clickable{if(s.location==null)change{it.copy(tab=AppTab.PLACES)}else onDate()}.testTag("plan_location"),fontSize=18.sp)
     TextButton(onClick={onPreview(local.plusDays(1).toInstant())},enabled=local.plusDays(1).year in 2000..2050,contentPadding=PaddingValues(0.dp),modifier=Modifier.width(32.dp).heightIn(min=48.dp).semantics{contentDescription="后一天"}){Text("›",fontSize=28.sp)}
    }
    SunDayArc(c,night,Modifier.fillMaxWidth().height(145.dp),light)
    Spacer(Modifier.height(12.dp))
    FrostedSurface(Modifier.fillMaxWidth(),opaque,night,25.dp) {
     Column(Modifier.padding(start=20.dp,end=20.dp,top=16.dp,bottom=8.dp)) {
      Text(featured?.let{(if(next!=null)"下一次" else "当日最后一次")+eventName(it)} ?: "当天的天空",fontSize=16.sp,color=color.copy(alpha=.85f))
      Text(featured?.event?.instant?.atZone(zone)?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "— —",fontSize=56.sp,fontWeight=FontWeight.Light,letterSpacing=(-2).sp,modifier=Modifier.padding(top=4.dp))
      Text(featured?.let{String.format(Locale.ROOT,"%s · %.0f°",cardinal(it.event.direction.azimuthDeg),it.event.direction.azimuthDeg)} ?: if(s.location==null)"选择地点后查看日月升落" else "当天没有升落事件",fontSize=17.sp,color=color.copy(alpha=.8f))
      HorizontalDivider(Modifier.padding(top=14.dp),color=color.copy(alpha=.14f))
      Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable {
       if(featured!=null)change{it.event(featured.target,featured.event.instant).copy(tab=AppTab.OBSERVE,controlsHidden=false)} else change{it.copy(tab=AppTab.PLACES)}
      }.testTag("view_event_in_sky"),verticalAlignment=Alignment.CenterVertically) {Text(if(featured!=null)"在天空中查看" else "选择观测地点",Modifier.weight(1f),fontSize=15.sp);SkyGlyph(SkyIcon.CHEVRON,Modifier.size(18.dp),color)}
     }
    }
    Spacer(Modifier.height(18.dp))
    if(s.location!=null&&c==null)Text("正在计算…",color=color.copy(alpha=.6f))
    c?.events?.forEachIndexed {i,t->
     Row(Modifier.fillMaxWidth().heightIn(min=58.dp).clickable{change{it.event(t.target,t.event.instant)}}.testTag("event_$i"),verticalAlignment=Alignment.CenterVertically) {
      SkyGlyph(targetIcon(t.target),Modifier.size(23.dp),if(night)NightRed else if(light)Color(0xFF71542E)else SunGold);Spacer(Modifier.width(16.dp));Text(eventName(t),Modifier.weight(1f),fontSize=18.sp)
      Text(t.event.instant.atZone(c.site.zone).format(DateTimeFormatter.ofPattern("HH:mm")),fontSize=21.sp,fontWeight=FontWeight.Light)
      Text(String.format(Locale.ROOT,"%.0f°",t.event.direction.azimuthDeg),Modifier.width(62.dp),textAlign=androidx.compose.ui.text.style.TextAlign.End,fontSize=17.sp,color=color.copy(alpha=.55f))
     }
     HorizontalDivider(color=color.copy(alpha=.13f))
    }
    Text("标准地平线 · 背景为示意",Modifier.padding(top=12.dp,bottom=16.dp),fontSize=11.sp,color=color.copy(alpha=.48f))
    FrostedSurface(Modifier.fillMaxWidth(),opaque,night,23.dp) {Column(Modifier.padding(horizontal=16.dp,vertical=8.dp)) {
     TargetPicker(s){target->change{it.copy(target=target)}}
     TimeControls(s,onPreview,onNow,onDate)
    }}
    c?.let {Text(String.format(Locale.ROOT,"银心  ·  真北 %.1f° / 高度 %.1f°",it.galaxy.center.azimuthDeg,it.galaxy.center.altitudeDeg),Modifier.padding(top=18.dp),fontSize=13.sp,color=color.copy(alpha=.7f))}
   }
  }
 }
}
@Composable private fun SunDayArc(c:SkyCalculation?,night:Boolean,modifier:Modifier,light:Boolean=false) {
 val rise=c?.events?.firstOrNull{it.target==SkyTarget.SUN&&it.event.kind==EventKind.RISE}
 val set=c?.events?.firstOrNull{it.target==SkyTarget.SUN&&it.event.kind==EventKind.SET}
 val gold=if(night)NightRed else if(light)Color(0xFF71542E) else SunGold
 val secondary=if(night)NightRed else if(light)Ink else MoonSilver
 Box(modifier.testTag("plan_sun_arc")) {
  Canvas(Modifier.fillMaxSize().padding(top=12.dp,bottom=42.dp,start=16.dp,end=16.dp)) {
   val start=Offset(8.dp.toPx(),size.height);val end=Offset(size.width-8.dp.toPx(),size.height)
   val p=Path().apply{moveTo(start.x,start.y);cubicTo(size.width*.30f,-size.height*.15f,size.width*.67f,-size.height*.15f,end.x,end.y)}
   drawPath(p,gold.copy(alpha=.85f),style=Stroke(1.dp.toPx()))
   drawLine(secondary.copy(alpha=.25f),Offset(0f,size.height),Offset(size.width,size.height),1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(),6.dp.toPx())))
   drawCircle(gold,3.dp.toPx(),start);drawCircle(gold,3.dp.toPx(),end)
  }
  Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.SpaceBetween) {
   listOf(rise to "日出",set to "日落").forEach{(event,name)->Column(horizontalAlignment=Alignment.CenterHorizontally){Text(name,fontSize=12.sp);Text(event?.event?.instant?.atZone(c!!.site.zone)?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "—",fontSize=12.sp,color=secondary.copy(alpha=.6f))}}
  }
 }
}
