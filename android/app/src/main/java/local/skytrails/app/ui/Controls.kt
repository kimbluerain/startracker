package local.skytrails.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import java.time.format.DateTimeFormatter
import local.skytrails.app.core.dayWindow
import local.skytrails.app.location.*

@Composable fun GlassPanel(modifier:Modifier=Modifier,dark:Boolean=true,opaque:Boolean=false,night:Boolean=false,content:@Composable ColumnScope.()->Unit) {
 FrostedSurface(modifier,opaque,night) {Column(Modifier.padding(18.dp),content=content)}
}
/** A live status is not a toggle. Only a time preview offers the return action. */
@Composable fun ObservationActions(s:AppState,onNow:()->Unit,onCollapse:()->Unit) {
 val accent=if(s.theme==AppThemeMode.NIGHT)NightRed else SunGold
 val foreground=if(s.theme==AppThemeMode.NIGHT)NightRed else MoonSilver
 val shape=RoundedCornerShape(16.dp)
 Row(Modifier.fillMaxWidth().padding(bottom=8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
  if(s.live) {
   Row(Modifier.heightIn(min=48.dp).clip(shape).background(accent.copy(alpha=.08f))
    .border(.5.dp,accent.copy(alpha=.22f),shape).padding(horizontal=14.dp)
    .testTag("live_time_status").semantics{stateDescription="当前时间，实时更新"},
    verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    Box(Modifier.size(6.dp).background(accent,RoundedCornerShape(3.dp)))
    Text("实时更新",color=accent,fontSize=13.sp,fontWeight=FontWeight.Medium)
   }
  } else {
   Button(onClick=onNow,modifier=Modifier.heightIn(min=48.dp).testTag("return_now"),shape=shape,
    border=BorderStroke(.7.dp,accent.copy(alpha=.65f)),contentPadding=PaddingValues(horizontal=14.dp),
    colors=ButtonDefaults.buttonColors(containerColor=accent.copy(alpha=.22f),contentColor=accent)) {
    Canvas(Modifier.size(17.dp)) {
     val center=Offset(size.width/2,size.height/2)
     drawCircle(accent,size.minDimension*.40f,center,style=androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx()))
     drawLine(accent,center,Offset(center.x,center.y-size.height*.22f),1.4.dp.toPx(),androidx.compose.ui.graphics.StrokeCap.Round)
     drawLine(accent,center,Offset(center.x+size.width*.20f,center.y+size.height*.09f),1.4.dp.toPx(),androidx.compose.ui.graphics.StrokeCap.Round)
    }
    Spacer(Modifier.width(8.dp));Text("回到现在",fontSize=13.sp,fontWeight=FontWeight.SemiBold)
   }
  }
  Spacer(Modifier.width(8.dp))
  OutlinedButton(onClick=onCollapse,modifier=Modifier.heightIn(min=48.dp).testTag("hide_controls"),shape=shape,
   border=BorderStroke(.5.dp,foreground.copy(alpha=.24f)),contentPadding=PaddingValues(horizontal=12.dp),
   colors=ButtonDefaults.outlinedButtonColors(containerColor=foreground.copy(alpha=.04f),contentColor=foreground)) {
   Text("收起面板",fontSize=12.sp,fontWeight=FontWeight.Medium)
   Spacer(Modifier.width(5.dp));SkyGlyph(SkyIcon.COLLAPSE,Modifier.size(17.dp),foreground.copy(alpha=.8f))
  }
 }
}
@Composable fun ObservationCategoryPicker(s:AppState,onSelect:(DisplayLayer)->Unit) {
 Row(Modifier.fillMaxWidth().testTag("observation_categories"),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
  DisplayLayer.entries.forEach {target->
   val enabled=s.observationCategory==target
   val shape=RoundedCornerShape(20.dp)
   val accent=if(s.theme==AppThemeMode.NIGHT)NightRed else SunGold
   val foreground=if(s.theme==AppThemeMode.NIGHT)NightRed else MoonSilver
   Column(Modifier.weight(1f).heightIn(min=48.dp).clip(shape)
    .background(if(enabled)accent.copy(alpha=.08f) else Color.Transparent)
    .border(.45.dp,if(enabled)accent.copy(alpha=.45f) else foreground.copy(alpha=.10f),shape)
    .clickable{onSelect(target)}.semantics{selected=enabled;role=androidx.compose.ui.semantics.Role.Tab}
    .testTag("category_${target.name}"),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
    SkyGlyph(target.icon,Modifier.size(16.dp),if(enabled)accent else foreground.copy(alpha=.4f))
    Spacer(Modifier.height(3.dp));Text(target.title,color=if(enabled)foreground else foreground.copy(alpha=.5f),fontSize=12.sp,maxLines=1)
   }
  }
 }
}
@Composable fun CompactTimeControls(s:AppState,onPreview:(Instant)->Unit,onNow:()->Unit,onDate:()->Unit,showDateRow:Boolean=true) {
 val foreground=if(s.theme==AppThemeMode.NIGHT)NightRed else MoonSilver
 val accent=if(s.theme==AppThemeMode.NIGHT)NightRed else SunGold
 val zone=s.location?.site?.zone ?: ZoneId.systemDefault();val local=s.instant.atZone(zone)
 val enabled=local.year in 2000..2050
 val day=dayWindow(local.toLocalDate(),zone);val length=Duration.between(day.start,day.endExclusive).toMillis().coerceAtLeast(1)
 val progress=(Duration.between(day.start,s.instant).toMillis().toDouble()/length).coerceIn(0.0,1.0).toFloat()
 if(showDateRow)Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
  TextButton(onClick=onDate,modifier=Modifier.testTag("edit_datetime"),contentPadding=PaddingValues(horizontal=0.dp)) {
   Text(local.format(DateTimeFormatter.ofPattern("MM月dd日")),fontSize=12.sp,color=foreground.copy(alpha=.65f));Spacer(Modifier.width(10.dp))
   Text(local.format(DateTimeFormatter.ofPattern("HH:mm")),fontSize=22.sp,fontWeight=FontWeight.Light)
  }
  Spacer(Modifier.weight(1f))
  TextButton(onClick=onNow,modifier=Modifier.testTag("return_now"),contentPadding=PaddingValues(horizontal=8.dp)) {Text(if(s.live)"实时" else "返回现在",fontSize=12.sp)}
 }
 if(!s.live)Text("时间预览 · ${local.offset}",Modifier.testTag("preview_label"),fontSize=11.sp,color=accent)
 Box(Modifier.fillMaxWidth().height(32.dp)) {
  Canvas(Modifier.fillMaxSize().padding(horizontal=8.dp)) {
   val y=size.height/2
   for(i in 0..36){val x=size.width*i/36;val h=if(i%6==0)7.dp.toPx() else 3.dp.toPx();drawLine(foreground.copy(alpha=.32f),Offset(x,y-h),Offset(x,y+h),1.dp.toPx())}
   val x=size.width*progress
   drawLine(accent.copy(alpha=.10f),Offset(x,y-11.dp.toPx()),Offset(x,y+11.dp.toPx()),10.dp.toPx())
   drawLine(accent,Offset(x,y-11.dp.toPx()),Offset(x,y+11.dp.toPx()),2.dp.toPx());drawCircle(accent,3.dp.toPx(),Offset(x,y-11.dp.toPx()))
  }
  Slider(progress,{timelinePreviewInstant(s.instant,zone,it.toDouble())?.let(onPreview)},enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("time_slider"),colors=SliderDefaults.colors(thumbColor=Color.Transparent,activeTrackColor=Color.Transparent,inactiveTrackColor=Color.Transparent,activeTickColor=Color.Transparent,inactiveTickColor=Color.Transparent))
 }
 if(!enabled)Text("请选择 2000—2050 年内的日期",Modifier.testTag("timeline_out_of_range"),fontSize=11.sp)
}
@Composable fun TargetPicker(s:AppState,onChange:(SkyTarget)->Unit) {
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
  SkyTarget.entries.forEach { t->TextButton(onClick={onChange(t)},modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("target_${t.name}").semantics{selected=s.target==t}) {
   Text(targetName(t),color=if(s.target==t) (if(MaterialTheme.colorScheme.onSurface.luminance()<.5f)MaterialTheme.colorScheme.primary else targetColor(t,s.theme==AppThemeMode.NIGHT)) else MaterialTheme.colorScheme.onSurface.copy(alpha=.75f),fontWeight=if(s.target==t)FontWeight.Bold else FontWeight.Normal)
  } }
 }
}
@Composable fun TimeControls(s:AppState,onPreview:(Instant)->Unit,onNow:()->Unit,onDate:()->Unit) {
 val zone=s.location?.site?.zone ?: ZoneId.systemDefault();val local=s.instant.atZone(zone)
 val timelineEnabled=local.year in 2000..2050
 val day=dayWindow(local.toLocalDate(),zone);val length=Duration.between(day.start,day.endExclusive).toMillis().coerceAtLeast(1)
 val progress=(Duration.between(day.start,s.instant).toMillis().toDouble()/length).coerceIn(0.0,1.0).toFloat()
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
  TextButton(onClick=onDate,modifier=Modifier.weight(1f).testTag("edit_datetime")) {Column(Modifier.fillMaxWidth()){Text(local.format(DateTimeFormatter.ofPattern("MM月dd日")),fontSize=13.sp);Text(local.format(DateTimeFormatter.ofPattern("HH:mm")),fontSize=32.sp,fontWeight=FontWeight.Medium)}}
  TextButton(onClick=onNow,modifier=Modifier.testTag("return_now")) {Text(if(s.live)"实时" else "返回现在")}
 }
 if(!s.live) Text("时间预览 · ${local.offset}",Modifier.testTag("preview_label"),fontSize=13.sp)
 val trackColor=MaterialTheme.colorScheme.onSurface
 val accent=MaterialTheme.colorScheme.primary
 Box(Modifier.fillMaxWidth().height(48.dp)) {
  Canvas(Modifier.fillMaxSize().padding(horizontal=12.dp)){val y=size.height/2;drawLine(trackColor.copy(alpha=.35f),Offset(0f,y),Offset(size.width,y),1.dp.toPx());for(i in 0..12){val x=size.width*i/12;drawLine(trackColor.copy(alpha=.4f),Offset(x,y-3.dp.toPx()),Offset(x,y+3.dp.toPx()),1.dp.toPx())};drawCircle(accent,5.dp.toPx(),Offset(size.width*progress,y))}
  Slider(value=progress,enabled=timelineEnabled,onValueChange={timelinePreviewInstant(s.instant,zone,it.toDouble())?.let(onPreview)},modifier=Modifier.fillMaxWidth().testTag("time_slider"),colors=SliderDefaults.colors(thumbColor=Color.Transparent,activeTrackColor=Color.Transparent,inactiveTrackColor=Color.Transparent,activeTickColor=Color.Transparent,inactiveTickColor=Color.Transparent))
 }
 if(!timelineEnabled)Text("当地日期超出 2000—2050 年，时间轴已停用；请选择日期或返回现在。",Modifier.testTag("timeline_out_of_range"),fontSize=12.sp)
 Text("${zone.id} · 标准地平线，未计入地形遮挡",fontSize=12.sp)
}
@Composable fun ManualDateTimeDialog(s:AppState,onDismiss:()->Unit,onChoose:(Instant)->Unit) {
 val zone=s.location?.site?.zone ?: ZoneId.systemDefault()
 var value by remember { mutableStateOf(s.instant.atZone(zone).toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))) }
 var message by remember { mutableStateOf<String?>(null) };var resolution by remember { mutableStateOf<LocalTimeResolution?>(null) };var local by remember { mutableStateOf<LocalDateTime?>(null) }
 AlertDialog(onDismissRequest=onDismiss,title={Text("选择当地日期与时间")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
  Text(zone.id);OutlinedTextField(value,{value=it;resolution=null;message=null},label={Text("yyyy-MM-dd HH:mm")},modifier=Modifier.testTag("datetime_input"))
  Text("支持 2000—2050 年",fontSize=13.sp)
  message?.let {Text(it,color=MaterialTheme.colorScheme.error)}
  when(val r=resolution) {
   is LocalTimeResolution.Gap-> {Text("此时间被夏令时跳过。下一有效时间：${r.nextValidLocalTime}");TextButton(onClick={local=r.nextValidLocalTime;onChoose(r.nextValidLocalTime.atZone(zone).toInstant());onDismiss()}){Text("采用下一有效时间")}}
   is LocalTimeResolution.Overlap-> {Text("此时间出现两次，请选择 UTC 偏移");r.offsets.forEach {offset->TextButton(onClick={onChoose(local!!.toInstant(offset));onDismiss()}){Text(offset.toString())}}}
   else->Unit
  }
 }},confirmButton={TextButton(onClick={runCatching {
  val l=parseManualDateTime(value);local=l
  val r=resolveLocalTime(l,zone);resolution=r;if(r is LocalTimeResolution.Resolved){onChoose(r.instant);onDismiss()}
 }.onFailure {message=it.message ?: "日期格式无效"} },modifier=Modifier.testTag("datetime_confirm")){Text("应用")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DateTimeDialog(s:AppState,onDismiss:()->Unit,onChoose:(Instant)->Unit) {
 val zone=s.location?.site?.zone ?: ZoneId.systemDefault()
 val initial=s.instant.atZone(zone)
 // Clamp only the chooser's initial proposal; keep the selected Instant until confirmation.
 val calendarDate=initial.toLocalDate().coerceIn(LocalDate.of(2000,1,1),LocalDate.of(2050,12,31))
 val calendar=rememberDatePickerState(initialSelectedDateMillis=calendarDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),yearRange=2000..2050)
 val time=rememberTimePickerState(initialHour=initial.hour,initialMinute=initial.minute,is24Hour=true)
 var stage by remember{mutableIntStateOf(0)};var manual by remember{mutableStateOf(false)}
 fun currentInput():DateTimeInput?=calendar.selectedDateMillis?.let {
  DateTimeInput(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().atTime(time.hour,time.minute),zone)
 }
 val input=currentInput()
 var choice by remember(input,stage){mutableStateOf<PendingDateTime?>(null)}
 var keyboard by remember{mutableStateOf(false)}
 fun choose(offset:ZoneOffset?=null) {
  val current=currentInput() ?: return
  choice?.choose(current,offset)?.let{onChoose(it);onDismiss()}
 }
 if(manual){ManualDateTimeDialog(s,onDismiss,onChoose);return}
 if(stage==0)DatePickerDialog(onDismissRequest=onDismiss,confirmButton={TextButton(onClick={if(calendar.selectedDateMillis!=null)stage=1},modifier=Modifier.testTag("calendar_next")){Text("选择时间")}},dismissButton={TextButton(onClick={manual=true}){Text("手动输入")}}) {
  DatePicker(state=calendar,title={Text("当地日期 · ${zone.id}",Modifier.padding(20.dp),fontSize=15.sp)},modifier=Modifier.testTag("local_calendar"))
 } else AlertDialog(onDismissRequest=onDismiss,title={Text("当地时间 · ${zone.id}")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
  Text(input?.local?.toLocalDate()?.toString() ?: "—")
  TextButton(onClick={keyboard=!keyboard},modifier=Modifier.testTag("time_input_mode")){Text(if(keyboard)"时间盘" else "键盘输入")}
  if(keyboard)TimeInput(state=time,modifier=Modifier.testTag("local_time_input"))
  else TimePicker(state=time,modifier=Modifier.testTag("local_time_picker"))
  when(val r=choice?.resolution){
   is LocalTimeResolution.Gap->{Text("此时间被夏令时跳过。下一有效时间：${r.nextValidLocalTime}");TextButton(onClick={choose()},modifier=Modifier.testTag("dst_gap")){Text("采用下一有效时间")}}
   is LocalTimeResolution.Overlap->{Text("此时间出现两次，请明确 UTC 偏移");r.offsets.forEach{offset->TextButton(onClick={choose(offset)},modifier=Modifier.testTag("dst_$offset")){Text(offset.toString())}}}
   else->Unit
  }
 }},confirmButton={TextButton(onClick={
  currentInput()?.let { current->
   val pending=PendingDateTime(current);choice=pending
   if(pending.resolution is LocalTimeResolution.Resolved)pending.choose(current)?.let{onChoose(it);onDismiss()}
  }
 },modifier=Modifier.testTag("datetime_confirm")){Text("应用")}},dismissButton={TextButton(onClick={stage=0},modifier=Modifier.testTag("calendar_back")){Text("返回日历")}})
}
