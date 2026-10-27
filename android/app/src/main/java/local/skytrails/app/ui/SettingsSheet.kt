package local.skytrails.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import local.skytrails.app.ui.renderer.CameraRuntime

/** Same-window modal: the measured sheet samples the actual sky/camera or shared photo backdrop. */
@Composable fun SettingsDialog(s:AppState,change:((AppState)->AppState)->Unit,runtime:CameraRuntime?=null,onDismiss:()->Unit) {
 var about by remember{mutableStateOf(false)}
 BackHandler{if(about)about=false else onDismiss()}
 val night=s.theme==AppThemeMode.NIGHT
 SkyTheme(s.theme) {
  val ink=MaterialTheme.colorScheme.onSurface;val accent=MaterialTheme.colorScheme.primary
  BoxWithConstraints(Modifier.fillMaxSize().testTag("settings_overlay").semantics{paneTitle="显示设置";isTraversalGroup=true}) {
   Box(Modifier.matchParentSize().background(Color.Black.copy(alpha=.28f)).clickable(onClick=onDismiss).clearAndSetSemantics{})
   val mask=if(runtime!=null)Modifier.glassMask(runtime,"settings_sheet",30.dp)else Modifier
   FrostedSurface(Modifier.align(Alignment.Center).padding(horizontal=24.dp,vertical=24.dp).widthIn(max=410.dp).fillMaxWidth().heightIn(max=maxHeight-48.dp).then(mask).testTag("settings_sheet"),s.reduceTransparency||s.theme==AppThemeMode.OUTDOOR,night,30.dp) {
    Column(Modifier.clickable(enabled=true,onClick={}).verticalScroll(rememberScrollState()).padding(horizontal=22.dp,vertical=14.dp)) {
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
      Column(Modifier.weight(1f)){Text("显示设置",fontSize=22.sp,fontWeight=FontWeight.Medium,color=ink);Text("让夜空更适合你",fontSize=11.sp,color=ink.copy(alpha=.55f),modifier=Modifier.padding(top=4.dp))}
      IconButton(onClick=onDismiss,modifier=Modifier.size(48.dp).testTag("settings_close").semantics{contentDescription="关闭设置"}){SkyGlyph(SkyIcon.CLOSE,Modifier.size(18.dp),ink.copy(alpha=.7f))}
     }
     Spacer(Modifier.height(24.dp));Text("外观",fontSize=11.sp,color=ink.copy(alpha=.55f),letterSpacing=1.sp)
     Row(Modifier.padding(top=10.dp,bottom=18.dp).fillMaxWidth().border(.5.dp,ink.copy(alpha=.12f),RoundedCornerShape(18.dp)).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
      listOf(AppThemeMode.SYSTEM to "系统",AppThemeMode.LIGHT to "浅色",AppThemeMode.DARK to "深色").forEach{(mode,label)->
       val active=s.theme==mode
       Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if(active)accent.copy(alpha=.15f)else Color.Transparent).selectable(active,role=Role.RadioButton,onClick={change{it.copy(theme=mode)}}).heightIn(min=48.dp).testTag("theme_${mode.name}"),contentAlignment=Alignment.Center){Text(label,fontSize=14.sp,color=if(active)accent else ink.copy(alpha=.72f),fontWeight=if(active)FontWeight.Medium else FontWeight.Normal)}
      }
     }
     listOf(Triple(AppThemeMode.OUTDOOR,"户外高对比","明亮环境，清晰不透明"),Triple(AppThemeMode.NIGHT,"夜间红光","降低亮度，保护暗适应")).forEach{(mode,title,subtitle)->
      val active=s.theme==mode
      Row(Modifier.fillMaxWidth().selectable(active,role=Role.RadioButton,onClick={change{it.copy(theme=mode)}}).heightIn(min=64.dp).testTag("theme_${mode.name}"),verticalAlignment=Alignment.CenterVertically) {
       SkyGlyph(if(mode==AppThemeMode.NIGHT)SkyIcon.MOON else SkyIcon.SUN,Modifier.size(19.dp),if(active)accent else ink.copy(alpha=.65f));Spacer(Modifier.width(13.dp))
       Column(Modifier.weight(1f)){Text(title,fontSize=14.sp,color=ink);Text(subtitle,fontSize=11.sp,color=ink.copy(alpha=.5f),modifier=Modifier.padding(top=3.dp))}
       RadioButton(active,null,modifier=Modifier.size(24.dp))
      }
     }
     HorizontalDivider(Modifier.padding(vertical=12.dp),thickness=.5.dp,color=ink.copy(alpha=.12f))
     Row(Modifier.fillMaxWidth().heightIn(min=56.dp).toggleable(s.reduceTransparency,role=Role.Switch,onValueChange={v->change{it.copy(reduceTransparency=v)}}).testTag("reduce_transparency"),verticalAlignment=Alignment.CenterVertically) {
      Text("减少透明度",Modifier.weight(1f),fontSize=14.sp,color=ink)
      Switch(s.reduceTransparency,null)
     }
     HorizontalDivider(Modifier.padding(top=12.dp,bottom=6.dp),thickness=.5.dp,color=ink.copy(alpha=.12f))
     Row(Modifier.fillMaxWidth().heightIn(min=52.dp).clickable{about=true}.testTag("about_open"),verticalAlignment=Alignment.CenterVertically){Text("关于 startracker 与开源许可",Modifier.weight(1f),fontSize=12.sp,color=ink.copy(alpha=.65f));SkyGlyph(SkyIcon.CHEVRON,Modifier.size(13.dp),ink.copy(alpha=.45f))}
    }
   }
  }
 }
 if(about)AboutDialog{about=false}
}
