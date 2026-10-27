package local.skytrails.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.*
import local.skytrails.app.core.dayWindow
import local.skytrails.app.ui.renderer.CameraRuntime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One persistent glass panel; only its detail area scrolls. Categories never move the sky. */
@Composable fun BottomObservationPanel(s:AppState,c:SkyCalculation?,runtime:CameraRuntime,change:((AppState)->AppState)->Unit,
 onPreview:(Instant)->Unit,onNow:()->Unit,onDate:()->Unit,maxHeight:Dp,scrollState:ScrollState,modifier:Modifier=Modifier) {
 val night=s.theme==AppThemeMode.NIGHT;val ink=if(night)NightRed else MoonSilver;val accent=if(night)NightRed else SunGold
 val category=s.observationCategory
 FrostedSurface(modifier.glassMask(runtime,"observation",28.dp),s.reduceTransparency||s.theme==AppThemeMode.OUTDOOR,night,28.dp) {
  Column(Modifier.heightIn(max=maxHeight).padding(horizontal=12.dp,vertical=8.dp).testTag("observation_controls")) {
   ObservationActions(s,onNow){change{it.copy(controlsHidden=true)}}
   ObservationCategoryPicker(s){change{state->state.copy(observationCategory=it)}}
   key(category) {
    Column(Modifier.weight(1f,fill=false).verticalScroll(scrollState).testTag("observation_detail_scroll")) {
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
      Row(Modifier.weight(1f).heightIn(min=48.dp).toggleable(s.displays(category),role=Role.Switch,onValueChange={change{it.toggleDisplayLayer(category)}}).testTag("display_layer_${category.name}"),verticalAlignment=Alignment.CenterVertically) {
       Text("显示",fontSize=12.sp,color=ink.copy(alpha=.8f));Spacer(Modifier.width(8.dp))
       Switch(checked=s.displays(category),onCheckedChange=null,colors=SwitchDefaults.colors(checkedThumbColor=ink,checkedTrackColor=accent.copy(alpha=.5f),uncheckedThumbColor=ink.copy(alpha=.5f),uncheckedTrackColor=ink.copy(alpha=.06f),uncheckedBorderColor=ink.copy(alpha=.2f)))
      }
      listOf(true to "全部开启",false to "全部关闭").forEach{(show,title)->
       TextButton(onClick={change{it.allDisplayLayers(show)}},modifier=Modifier.heightIn(min=48.dp).testTag(if(show)"layers_all_on" else "layers_all_off"),contentPadding=PaddingValues(horizontal=8.dp)){Text(title,fontSize=11.sp,color=if(show)accent else ink.copy(alpha=.7f))}
      }
     }
     HorizontalDivider(thickness=.5.dp,color=ink.copy(alpha=.1f))
     if(category==DisplayLayer.STARS)StarCategoryContent(s,c,change)
     else {
      val target=category.target!!
      val d=when(target){SkyTarget.SUN->c?.sun;SkyTarget.MOON->c?.moon;SkyTarget.GALAXY->c?.galaxy?.center}
      Row(Modifier.fillMaxWidth().heightIn(min=64.dp),verticalAlignment=Alignment.CenterVertically) {
       Column(Modifier.weight(1f)) {
        Text(category.title+when{!s.displays(category)->" · 已隐藏";d==null->"";d.altitudeDeg<0->" · 地平线下";else->" · 地平线上"},fontSize=14.sp,color=ink)
        Text(d?.let{String.format(Locale.ROOT,"方位 %.0f° · 高度 %.0f°",it.azimuthDeg,it.altitudeDeg)}?:"选择地点后计算",fontSize=11.sp,color=ink.copy(alpha=.6f))
        c?.let{calculation->
         val zone=calculation.site.zone;val day=dayWindow(calculation.at.atZone(zone).toLocalDate(),zone);val f=DateTimeFormatter.ofPattern("MM/dd HH:mm")
         Text(if(target==SkyTarget.GALAXY)"银心位置 · 银河带为示意" else "当日轨迹 ${f.format(day.start.atZone(zone))} — ${f.format(day.endExclusive.atZone(zone))}",fontSize=9.sp,color=ink.copy(alpha=.5f))
        }
       }
       TextButton(onClick={change{it.focus(target,c?.at?:it.instant)}},enabled=d!=null,modifier=Modifier.heightIn(min=48.dp).testTag("focus_${target.name}"),contentPadding=PaddingValues(horizontal=8.dp)){Text("定位",fontSize=12.sp)}
      }
     }
     HorizontalDivider(thickness=.5.dp,color=ink.copy(alpha=.1f))
     val local=s.instant.atZone(s.location?.site?.zone?:ZoneId.systemDefault())
     TextButton(onClick=onDate,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("edit_datetime"),contentPadding=PaddingValues(0.dp)) {
      Text(local.format(DateTimeFormatter.ofPattern("HH:mm")),fontSize=22.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.Light,color=accent)
      Spacer(Modifier.width(12.dp));Text(local.format(DateTimeFormatter.ofPattern("MM月dd日")),fontSize=11.sp,color=ink.copy(alpha=.65f))
      Spacer(Modifier.weight(1f));Text("选择时间",fontSize=11.sp,color=ink.copy(alpha=.6f))
     }
     CompactTimeControls(s,onPreview,onNow,onDate,showDateRow=false)
    }
   }
  }
 }
}
