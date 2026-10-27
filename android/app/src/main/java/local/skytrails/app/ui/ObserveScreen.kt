package local.skytrails.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import local.skytrails.app.location.*
import local.skytrails.app.core.StarCatalog
import local.skytrails.app.ui.sky.*
import local.skytrails.app.ui.renderer.*
import local.skytrails.app.ar.PhysicalSite
import java.time.Instant
import java.util.Locale

@Composable fun ObserveScreen(s:AppState,c:SkyCalculation?,runtime:CameraRuntime,change:((AppState)->AppState)->Unit,onPreview:(Instant)->Unit,onNow:()->Unit,onDate:()->Unit,onSettings:()->Unit,requestCamera:()->Unit,hudVisible:Boolean=true) {
 val night=s.theme==AppThemeMode.NIGHT
 val color=if(night)NightRed else MoonSilver
 val accent=if(night)NightRed else SunGold
 val opaque=s.reduceTransparency||s.theme==AppThemeMode.OUTDOOR
 var calibration by remember{mutableStateOf(false)};var details by remember{mutableStateOf(false)}
 val panelScrolls=DisplayLayer.entries.associateWith{rememberScrollState()}
 val sharedPose=rememberObservePose(runtime,s)
 val camera=s.cameraEligible(s.realNow)&&!s.virtualSky
 var cameraStatus by remember{mutableStateOf("等待相机与传感器")}
 LaunchedEffect(camera){while(camera){cameraStatus=listOfNotNull(runtime.view?.state,runtime.pose?.quality,runtime.calibrationStatus()).joinToString(" · ");kotlinx.coroutines.delay(500)}}
 DisposableEffect(runtime){onDispose{runtime.windowMasks.clear();runtime.masks()}}
 BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF101216))) {
  val screenHeight=maxHeight
  if(camera) {val site=s.location!!.site;CameraScene(runtime,PhysicalSite(site.latitude,site.longitude,site.elevationMeters+site.eyeHeightMeters),c,s.target,night,!opaque,Modifier.fillMaxSize().testTag("camera_host"),s.layers,hudVisible=hudVisible,starLayers=s.starLayers,starFocusId=s.starFocusId)}
  else VirtualSkyScene(s,c,runtime,sharedPose,change,Modifier.fillMaxSize(),hudVisible=hudVisible)
  if(hudVisible) {
  // These transparent scrims never reserve sky space or change the camera viewport.
  Box(Modifier.fillMaxWidth().height(190.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.60f),Color.Transparent))))
  Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(250.dp).background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.40f)))))
  DarkGlassContent(night) {
   Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal=22.dp,vertical=8.dp).fillMaxWidth().testTag("observe_header")) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
     Column(Modifier.weight(1f)) {
      Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
       FrostedSurface(Modifier.size(32.dp).glassMask(runtime,"wordmark",11.dp),opaque,night,11.dp){SkyGlyph(SkyIcon.TRAIL,Modifier.align(Alignment.Center).size(24.dp),accent)}
       Text("startracker",fontSize=22.sp,fontWeight=FontWeight.Medium,letterSpacing=(-.35).sp,color=color)
      }
      Row(Modifier.heightIn(min=40.dp).clickable{change{it.copy(tab=AppTab.PLACES)}}.testTag("observe_location"),verticalAlignment=Alignment.CenterVertically) {
       SkyGlyph(SkyIcon.PIN,Modifier.size(13.dp),color.copy(alpha=.7f));Spacer(Modifier.width(5.dp))
       Text(s.location?.name ?: "选择观测地点",fontSize=12.sp,color=color.copy(alpha=.75f))
      }
     }
     FrostedSurface(Modifier.size(48.dp).then(Modifier.glassMask(runtime,"settings",24.dp)),opaque,night,24.dp) {
      IconButton(onClick=onSettings,modifier=Modifier.fillMaxSize().testTag("settings").semantics{contentDescription="显示设置"}){SkyGlyph(SkyIcon.SETTINGS,Modifier.size(21.dp),color.copy(alpha=.85f))}
     }
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
     TextButton(onClick={details=true},modifier=Modifier.weight(1f).heightIn(min=40.dp).testTag("observe_status"),contentPadding=PaddingValues(0.dp)) {
      Box(Modifier.size(4.dp).background(if(camera)accent else color.copy(alpha=.65f),RoundedCornerShape(2.dp)))
      Spacer(Modifier.width(6.dp))
      Text(when {s.location==null->"尚未设置地点";s.location.source==LocationSource.DEVICE&&!s.physicalEligible(s.realNow)->"定位已过期 · 点此更新";camera->"实景 · 方向待核验";s.skyFollow&&sharedPose!=null->"星空 · 跟随手机";else->"星空 · 手动浏览"},fontSize=11.sp,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,color=color.copy(alpha=.72f),modifier=if(s.location==null)Modifier.testTag("unset_location") else Modifier)
     }
     if(!camera) {
      val following=s.skyFollow&&sharedPose!=null
      FrostedSurface(Modifier.width(108.dp).height(48.dp).glassMask(runtime,"follow",24.dp),opaque,night,24.dp) {
       TextButton(onClick={change{it.copy(skyFollow=true)}},enabled=sharedPose!=null,
        modifier=Modifier.fillMaxSize().testTag("sky_follow").semantics{stateDescription=if(following)"正在跟随手机方向" else "手动浏览"},
        colors=ButtonDefaults.textButtonColors(containerColor=if(following)accent.copy(alpha=.15f)else Color.Transparent),contentPadding=PaddingValues(horizontal=10.dp)) {
        SkyGlyph(SkyIcon.LOCATION,Modifier.size(17.dp),if(sharedPose!=null)accent else color.copy(alpha=.35f));Spacer(Modifier.width(6.dp))
        Text(if(following)"跟随中" else "跟随手机",fontSize=13.sp,fontWeight=FontWeight.Medium,color=if(sharedPose!=null)accent else color.copy(alpha=.35f))
       }
      }
      Spacer(Modifier.width(8.dp))
     }
     FrostedSurface(Modifier.size(48.dp).glassMask(runtime,"mode",24.dp),opaque,night,24.dp) {
      IconButton(onClick={
       if(camera)change{it.copy(virtualSky=true)}
       else if(s.physicalEligible(s.realNow)){change{it.requestCameraMode(it.realNow)};if(!s.cameraPermission)requestCamera()}
       else details=true
      },modifier=Modifier.fillMaxSize().testTag("mode_toggle").semantics {
       contentDescription=if(camera)"切换到星空" else "切换到实景 AR"
       stateDescription=if(camera)"当前：实景 AR" else "当前：星空"
      }) {
       SkyGlyph(if(camera)SkyIcon.AR_VIEW else SkyIcon.SKY_VIEW,Modifier.size(24.dp),accent)
      }
     }
    }
   }
   if(!s.controlsHidden) {
    BottomObservationPanel(s,c,runtime,change,onPreview,onNow,onDate,screenHeight*.49f,panelScrolls.getValue(s.observationCategory),
     Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start=16.dp,end=16.dp,bottom=76.dp).widthIn(max=560.dp).fillMaxWidth())
   } else {
    FrostedSurface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom=16.dp).then(Modifier.glassMask(runtime,"restore",24.dp)),opaque,night,24.dp) {
     TextButton(onClick={change{it.copy(controlsHidden=false)}},modifier=Modifier.heightIn(min=48.dp).testTag("restore_controls"),contentPadding=PaddingValues(horizontal=22.dp)) {
      SkyGlyph(SkyIcon.EXPAND,Modifier.size(19.dp),color);Spacer(Modifier.width(9.dp))
      Text("展开面板",color=color,fontSize=14.sp,fontWeight=FontWeight.Medium)
     }
    }
   }
  }
 }
 }
 if(details&&hudVisible)AlertDialog(onDismissRequest={details=false},title={Text(if(camera)"实景观测" else "星空与位置")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Text(if(camera)cameraStatus else if(sharedPose!=null)"方向来自手机完整姿态，真北已按现场位置校正。拖动进入手动浏览，点击跟随恢复。" else "手动浏览：需要有效现场位置和方向传感器才能跟随手机。")
  s.location?.let{Text(locationDescription(it),fontSize=13.sp)} ?: Text("使用手机位置，或在地点页选择城市、输入 WGS84 坐标。")
  if(s.location?.source==LocationSource.DEVICE&&!s.physicalEligible(s.realNow))Text("定位已过期或权限撤销，请前往地点页刷新。")
  Text("日月轨迹从当地所选日的起点画至次日的起点，起止位置并不重合；端点标明日期，不强制闭环。星座标记按星表、地点和时间计算，不是相机图像识别。日月轨迹和银心按地点、时间计算；银河 ±5° 是示意带。实际可见性受云量、月光与光污染影响。星图不表示现场天气、亮度或地形遮挡。",fontSize=13.sp)
  if(camera)TextButton(onClick={details=false;calibration=true},modifier=Modifier.testTag("calibration_open")){Text("方向校准")}
  TextButton(onClick={details=false;change{it.copy(tab=AppTab.PLACES)}}){Text("选择或更新地点")}
 }},confirmButton={TextButton(onClick={details=false}){Text("完成")}})
 if(calibration&&hudVisible)CalibrationDialog(runtime){calibration=false}
}
