package local.skytrails.app.ui.renderer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.isActive
import local.skytrails.app.ar.*
import local.skytrails.app.core.SkyDirection
import local.skytrails.app.ui.*
import local.skytrails.app.ui.sky.*
import java.time.Instant
import java.util.Locale

class CameraRuntime {
 var view:CameraGlassView?=null
 var pose:PoseAdapter?=null
 var skyView:SkyGlassView?=null
 var displayDegrees=0
 var manualSkyBasis:SkyViewBasis?=null
 var skyFieldOfView=SkyZoom.DEFAULT_FOV
 val skyFocus=SkyFocusConsumption()
 private var lastCalibrationKey:CalibrationKey?=null
 val calibration=CalibrationStore()
 var originX=0f;var originY=0f
 val windowMasks=mutableStateMapOf<String,GlassMask>()
 fun masks(){val local=windowMasks.values.map{GlassSampling.localMask(it,originX,originY)};view?.let{it.masks=local;it.requestRender()};skyView?.let{it.masks=local}}
 fun rememberCalibrationContext(image:CameraImage?) {image?.capture?.let{lastCalibrationKey=it.calibrationKey(image.preview)}}
 fun skyOffset(display:Int,site:PhysicalSite,now:Instant=Instant.now()):ManualOffset {val key=lastCalibrationKey ?: return ManualOffset();return calibration.resolve(key.copy(displayDegrees=display),site,now).offset}
 fun offset():ManualOffset {
  val frame=view?.image?.get() ?: return ManualOffset();val capture=frame.capture ?: return ManualOffset();val pose=pose?.frame?.get() ?: return ManualOffset()
  rememberCalibrationContext(frame)
  return calibration.resolve(capture.calibrationKey(frame.preview),pose.site,Instant.now()).offset
 }
 fun calibrationStatus():String? {
  val frame=view?.image?.get() ?: return null;val capture=frame.capture ?: return "匹配光学参数不足，AR 已停用"
  if(capture.quality==OpticsQuality.UNAVAILABLE)return "镜头参数不足，仅显示预览"
  val p=pose?.frame?.get() ?: return "姿态不可用，可切换虚拟天空"
  return when(calibration.resolve(capture.calibrationKey(frame.preview),p.site,Instant.now()).status) {
   CalibrationStatus.PENDING->"方向待校准 · 镜头为元数据估算"
   CalibrationStatus.APPLIED->"方向偏移已应用 · 镜头为元数据估算"
   CalibrationStatus.EXPIRED->"校准已过期，偏移已撤销"
  }
 }
 fun center():SkyDirection? {
  val image=view?.image?.get() ?: return null;val capture=image.capture ?: return null
  if(capture.quality==OpticsQuality.UNAVAILABLE)return null
  val preview=image.preview;val k=capture.optics.estimate() ?: return null
  val point=preview.viewToStream.map(Pixel(preview.viewWidth/2.0,preview.viewHeight/2.0))
  val crop=streamCrop(capture.crop ?: return null,capture.streamWidth,capture.streamHeight) ?: return null
  val pixel=Pixel(crop.left+point.x*crop.width/capture.streamWidth,crop.top+point.y*crop.height/capture.streamHeight)
  val sensorRay=CameraVector((pixel.x-k.cx)/k.fx,(k.cy-pixel.y)/k.fy,1.0)
  return pose?.frame?.get()?.fromCamera(deviceCameraToSensor(sensorRay,(360-preview.sensorDegrees)%360))
 }
}
@Composable fun CameraScene(runtime:CameraRuntime,site:PhysicalSite,calculation:SkyCalculation?,target:SkyTarget,night:Boolean,blur:Boolean,modifier:Modifier=Modifier,layers:SkyLayers=SkyLayers(),hudVisible:Boolean=true,starLayers:StarLayers=StarLayers(),starFocusId:String?=null) {
 val context=LocalContext.current;val owner=LocalLifecycleOwner.current
 val view=remember {CameraGlassView(context)}
 var tick by remember{mutableLongStateOf(0L)}
 runtime.view=view;view.blurEnabled=blur
 DisposableEffect(view,owner) {
  val observer=object:DefaultLifecycleObserver {
   override fun onStart(owner:LifecycleOwner){view.activate()}
   override fun onStop(owner:LifecycleOwner){runtime.rememberCalibrationContext(view.image.get());view.deactivate()}
  }
  owner.lifecycle.addObserver(observer)
  onDispose {owner.lifecycle.removeObserver(observer);runtime.rememberCalibrationContext(view.image.get());view.deactivate();runtime.view=null}
 }
 LaunchedEffect(view){while(isActive){withFrameNanos{tick=it}}}
 Box(modifier) {
  AndroidView(factory={view},modifier=Modifier.fillMaxSize().onGloballyPositioned{val b=it.boundsInWindow();runtime.originX=b.left;runtime.originY=b.top;runtime.masks()})
  val current=remember(tick){view.image.get()};val position=remember(tick){runtime.pose?.frame?.get()}
  val projection=if(current?.capture!=null&&position!=null)SkyProjection.camera(position,current.capture,current.preview,runtime.offset()) else null
  SkyCanvas(calculation,target,night,Modifier.fillMaxSize(),projection,physical=true,exclusions=runtime.windowMasks.values.toList(),layers=layers,showGuidance=hudVisible,starLayers=starLayers,starFocusId=starFocusId)
 }
}
@Composable fun CalibrationDialog(runtime:CameraRuntime,onDismiss:()->Unit) {
 var yaw by remember{mutableFloatStateOf(runtime.offset().yawDegrees.toFloat())};var pitch by remember{mutableFloatStateOf(runtime.offset().pitchDegrees.toFloat())}
 var message by remember{mutableStateOf<String?>(null)};var center by remember{mutableStateOf(runtime.center())}
 LaunchedEffect(Unit){while(isActive){kotlinx.coroutines.delay(300);center=runtime.center()}}
 AlertDialog(onDismissRequest=onDismiss,title={Text("方向校准")},text={Column(Modifier.verticalScroll(rememberScrollState()).testTag("calibration_body")) {
  Text(center?.let{String.format(Locale.ROOT,"画面中心 · 真北 %.1f°\n高度 %.1f°（传感器原始方向）",it.azimuthDeg,it.altitudeDeg)} ?: "中心方向不可用：需现场位置、姿态与匹配镜头参数")
  Text("对准已知方向后调整。这里只校准方向，镜头仍为元数据估算。",fontSize=13.sp)
  Text(String.format(Locale.ROOT,"水平偏移 %+.1f°",yaw));Slider(yaw,{yaw=it},valueRange=-30f..30f,modifier=Modifier.heightIn(min=48.dp).testTag("calibration_yaw"))
  Text(String.format(Locale.ROOT,"垂直偏移 %+.1f°",pitch));Slider(pitch,{pitch=it},valueRange=-30f..30f,modifier=Modifier.heightIn(min=48.dp).testTag("calibration_pitch"))
  Text("换镜头、方向、地点或超过24小时后须重新确认。",modifier=Modifier.testTag("calibration_end"),fontSize=12.sp)
  message?.let{Text(it)}
 }},confirmButton={TextButton(onClick={val frame=runtime.view?.image?.get();val p=runtime.pose?.frame?.get();val capture=frame?.capture
  if(frame!=null&&p!=null&&capture?.quality==OpticsQuality.METADATA_ESTIMATE){runtime.calibration.set(capture.calibrationKey(frame.preview),p.site,Instant.now(),ManualOffset(yaw.toDouble(),pitch.toDouble()));onDismiss()}else message="参数不足，未保存校准"
 },modifier=Modifier.testTag("calibration_save")){Text("确认偏移")}},dismissButton={TextButton(onClick={runtime.calibration.clear();onDismiss()},modifier=Modifier.testTag("calibration_dismiss")){Text("清除 / 关闭")}})
}
