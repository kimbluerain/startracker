package local.skytrails.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import local.skytrails.app.core.*
import kotlin.math.*

/** Inline content inside the single bottom observation panel. */
@Composable fun StarCategoryContent(s:AppState,c:SkyCalculation?,change:((AppState)->AppState)->Unit) {
 val night=s.theme==AppThemeMode.NIGHT;val ink=if(night)NightRed else MoonSilver;val accent=if(night)NightRed else SunGold
 Column(Modifier.fillMaxWidth().padding(top=4.dp,bottom=6.dp)) {
  Row(Modifier.fillMaxWidth().heightIn(min=54.dp),verticalAlignment=Alignment.CenterVertically) {
   Column(Modifier.weight(1f)) {
    Text("4 组星座与星群",fontSize=11.sp,color=ink.copy(alpha=.65f))
    Text(if(s.starLayers.masterVisible)"向上滑动查看更多" else "星星已隐藏",fontSize=10.sp,color=ink.copy(alpha=.45f))
   }
   Row(Modifier.heightIn(min=48.dp).toggleable(s.starLayers.twinkle,role=Role.Switch,onValueChange={v->change{it.copy(starLayers=it.starLayers.copy(twinkle=v))}}).testTag("star_twinkle").semantics{contentDescription="星星闪烁"},verticalAlignment=Alignment.CenterVertically) {
    Text("闪烁",fontSize=13.sp,color=ink);Spacer(Modifier.width(6.dp))
    Switch(checked=s.starLayers.twinkle,onCheckedChange=null,colors=SwitchDefaults.colors(checkedThumbColor=ink,checkedTrackColor=accent.copy(alpha=.5f),uncheckedThumbColor=ink.copy(alpha=.5f),uncheckedTrackColor=ink.copy(alpha=.06f),uncheckedBorderColor=ink.copy(alpha=.2f)))
   }
  }
  StarCatalog.constellations.forEach {objectInfo->
   val enabled=objectInfo.id in s.starLayers.enabled;val d=c?.stars?.centers?.get(objectInfo.id)
   HorizontalDivider(thickness=.5.dp,color=ink.copy(alpha=.1f))
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
    Row(Modifier.weight(1f).heightIn(min=70.dp).testTag("star_info_${objectInfo.id}"),verticalAlignment=Alignment.CenterVertically) {
     ConstellationGlyph(objectInfo,Modifier.size(28.dp),if(enabled)accent else ink.copy(alpha=.3f));Spacer(Modifier.width(9.dp))
     Column(Modifier.weight(1f)) {
      Text(objectInfo.name+if(!s.starLayers.shows(objectInfo.id))" · 已隐藏" else if(d!=null&&d.altitudeDeg<0)" · 地平线下" else "",fontSize=12.sp,color=if(enabled)ink else ink.copy(alpha=.45f))
      Text(d?.let{java.lang.String.format(java.util.Locale.ROOT,"方位 %.0f° · 高度 %.0f°",it.azimuthDeg,it.altitudeDeg)}?:"选择地点后计算",fontSize=10.sp,color=ink.copy(alpha=.5f))
     }
     Checkbox(checked=enabled,onCheckedChange={change{it.copy(starLayers=it.starLayers.toggle(objectInfo.id))}},modifier=Modifier.testTag("star_object_${objectInfo.id}"),colors=CheckboxDefaults.colors(checkedColor=accent.copy(alpha=.8f),uncheckedColor=ink.copy(alpha=.3f),checkmarkColor=Color(0xFF15171B)))
    }
    TextButton(onClick={change{it.focusConstellation(objectInfo.id,c?.at?:it.instant)}},enabled=d!=null,modifier=Modifier.heightIn(min=48.dp).testTag("star_focus_${objectInfo.id}"),contentPadding=PaddingValues(horizontal=8.dp)){Text("定位",fontSize=12.sp)}
   }
  }
  HorizontalDivider(Modifier.padding(top=6.dp),thickness=.5.dp,color=ink.copy(alpha=.1f))
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
   listOf(Triple("stars","亮星标记",s.starLayers.stars),Triple("lines","连线",s.starLayers.lines),Triple("names","名称",s.starLayers.names)).forEach{(id,title,active)->
    Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if(active)accent.copy(alpha=.1f)else ink.copy(alpha=.035f))
     .border(.5.dp,if(active)accent.copy(alpha=.3f)else ink.copy(alpha=.09f),RoundedCornerShape(14.dp))
     .toggleable(active,role=Role.Checkbox,onValueChange={v->change{it.copy(starLayers=when(id){"stars"->it.starLayers.copy(stars=v);"lines"->it.starLayers.copy(lines=v);else->it.starLayers.copy(names=v)})}})
     .heightIn(min=48.dp).testTag("star_layer_$id"),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
      Text(title,fontSize=11.sp,color=if(active)accent else ink.copy(alpha=.55f))
    }
   }
  }
 }
}

/** Schematic icon derived from this pattern's real J2000 positions; no raster art. */
@Composable private fun ConstellationGlyph(pattern:Constellation,modifier:Modifier,color:Color) {
 val points=remember(pattern.id){
  val stars=StarCatalog.stars.filter{it.hip in pattern.members};val dec=stars.map{it.decDeg}.average()
  val raw=stars.associate{it.hip to Pair(-it.raDeg*cos(Math.toRadians(dec)),-it.decDeg)}
  val minX=raw.values.minOf{it.first};val minY=raw.values.minOf{it.second};val span=max(raw.values.maxOf{it.first}-minX,raw.values.maxOf{it.second}-minY)
  raw.mapValues{(_,p)->Offset(((p.first-minX)/span).toFloat(),((p.second-minY)/span).toFloat())}
 }
 Canvas(modifier){
  fun at(hip:Int)=points.getValue(hip)*size.minDimension*.82f+Offset(size.width*.09f,size.height*.09f)
  pattern.edges.forEach{(a,b)->drawLine(color.copy(alpha=.65f),at(a),at(b),.8.dp.toPx())}
  pattern.members.forEach{drawCircle(color,1.5.dp.toPx(),at(it))}
 }
}
