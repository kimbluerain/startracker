package local.skytrails.app.ui.renderer
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.opengl.GLES11Ext
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import eu.domob.heliodos.BuildConfig
import local.skytrails.app.ar.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

 data class CameraImage(val capture:CaptureGeometry?,val preview:PreviewGeometry)
/** Camera2 owns its output Surface; this host owns input SurfaceTexture after camera close. */
class CameraGlassView(context:Context):GLSurfaceView(context),GLSurfaceView.Renderer,DisplayManager.DisplayListener {
 private val controller=Camera2Controller(context)
 private val displays=context.getSystemService(DisplayManager::class.java)
 private val pending=AtomicBoolean(false)
 val image=AtomicReference<CameraImage?>(null)
 @Volatile var masks:List<GlassMask> = emptyList()
 @Volatile var blurEnabled=true
 @Volatile var state="等待相机画面";private set
 @Volatile private var active=false
 @Volatile private var dead=false
 @Volatile private var rotation=0
 @Volatile private var epoch=0L
 @Volatile private var streamW=0
 @Volatile private var streamH=0
 @Volatile private var sensor:Int?=null
 @Volatile private var widthPx=0
 @Volatile private var heightPx=0
 private var version=0L
 @Volatile private var input:SurfaceTexture?=null
 private var texture=0
 private var pipeline:GlassPipeline?=null
 @Volatile private var contextVersion=0L
 @Volatile private var frameReady=false
 private var sampling=GlassPipeline.FLIP
 private var windowStart=SystemClock.elapsedRealtime()
 private var consumed=0L;private var draws=0L;private var renderCalls=0L;private var cpuNs=0L;private var lastTimestamp=0L;private var intervals=0L;private var intervalNs=0L
 init {
  setEGLContextClientVersion(2);preserveEGLContextOnPause=true;setRenderer(this);renderMode=RENDERMODE_WHEN_DIRTY
  controller.onState={state=it}
  controller.onConfiguration={e,size,s->epoch=e;streamW=size.width;streamH=size.height;sensor=s;requestRender()}
  controller.onInvalidated={image.set(null);frameReady=false;requestRender()}
  controller.onReconfigure={if(active)open()}
 }
 fun activate() {if(active||dead)return;active=true;rotation=displayRotation();displays.registerDisplayListener(this,null);open()}
 fun deactivate() {active=false;image.set(null);frameReady=false;displays.unregisterDisplayListener(this);controller.stop();requestRender()}
 private fun open() {
  if(!active||dead||input==null)return
  controller.start {output,ready->
   val token=contextVersion
   queueEvent {
    val current=input
    if(!active||dead||current==null||token!=contextVersion) {post{ready(null)};return@queueEvent}
    current.setDefaultBufferSize(output.size.width,output.size.height)
    streamW=output.size.width;streamH=output.size.height;sensor=output.sensorDegrees;epoch=0
    val surface=Surface(current);post{if(dead||!active||token!=contextVersion){surface.release();ready(null)}else ready(surface)}
   }
  }
 }
 override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?) {
  contextVersion++;val old=input;input=null;frameReady=false;image.set(null)
  // A lost GL context never permits its previous camera input to be reused.
  runCatching {
   pipeline=GlassPipeline();val ids=IntArray(1);glGenTextures(1,ids,0);texture=ids[0]
   glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);GlassPipeline.parameters(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
   input=SurfaceTexture(texture).apply{setOnFrameAvailableListener{pending.set(true);requestRender()}}
  }.onFailure {state="GPU 相机不可用，可继续计划与数值观测";pipeline=null;post{controller.stop()}}
  post {controller.stop {old?.release();if(active)open()}}
 }
 override fun onSurfaceChanged(gl:GL10?,width:Int,height:Int) {widthPx=width;heightPx=height;version++;pipeline?.resize(width,height);requestRender()}
 override fun onDrawFrame(gl:GL10?) {
  val start=SystemClock.elapsedRealtimeNanos();val p=pipeline ?: return
  if(!active||dead){p.clear();image.set(null);return}
  val st=input ?: return
  if(pending.getAndSet(false)) {
   if(runCatching {st.updateTexImage()}.isFailure){state="相机纹理暂不可用";frameReady=false;image.set(null);p.clear();return}
   val frame=controller.frameForTimestamp(st.timestamp)
   val s=sensor
   if(frame!=null&&s in listOf(0,90,180,270)&&widthPx>0&&heightPx>0&&streamW>0&&streamH>0) {
    val preview=PreviewGeometry.create(version,streamW,streamH,widthPx,heightPx,s!!,rotation,frame.epoch)
    val actual=FloatArray(16);st.getTransformMatrix(actual)
    val a=Mat3.of(actual[0].toDouble(),actual[4].toDouble(),actual[12].toDouble(),actual[1].toDouble(),actual[5].toDouble(),actual[13].toDouble())
    val t=(GlassSampling.viewToSampler(preview,a)*Mat3.scaleTranslate(widthPx.toDouble(),heightPx.toDouble())).values()
    sampling=floatArrayOf(t[0].toFloat(),t[3].toFloat(),0f,0f,t[1].toFloat(),t[4].toFloat(),0f,0f,0f,0f,1f,0f,t[2].toFloat(),t[5].toFloat(),0f,1f)
    val qualified=GlassSampling.residualAllowsOverlay(preview,a)
    frameReady=true;image.set(CameraImage(if(qualified)frame.geometry else null,preview));consumed++
    state=if(!qualified)"新鲜预览可用；画面变换未经验证，暂停 AR" else if(frame.geometry?.quality==OpticsQuality.METADATA_ESTIMATE)"新鲜相机预览 · 镜头参数为估算" else "新鲜相机预览 · 光学参数不足，仅预览"
    if(lastTimestamp>0&&st.timestamp>lastTimestamp){intervalNs+=st.timestamp-lastTimestamp;intervals++};lastTimestamp=st.timestamp
   } else if(frame!=null&&widthPx>0&&heightPx>0) {
    // Unknown mounting forbids AR, but fresh output is still an ordinary preview.
    val actual=FloatArray(16);st.getTransformMatrix(actual);sampling=GlassSampling.unmountedViewToSampler(actual);frameReady=true;image.set(null);consumed++
    state="新鲜预览可用；安装方向参数不足，暂停 AR"
   } else {frameReady=false;image.set(null)}
  }
  if(frameReady)runCatching{p.draw(texture,GLES11Ext.GL_TEXTURE_EXTERNAL_OES,sampling,masks,blurEnabled);draws++}.onFailure{state="GPU 绘制不可用，使用虚拟天空";frameReady=false;image.set(null);p.clear()}
  else p.clear()
  renderCalls++;cpuNs+=SystemClock.elapsedRealtimeNanos()-start
  if(BuildConfig.DEBUG&&SystemClock.elapsedRealtime()-windowStart>=30000)telemetry()
 }
 private fun telemetry(){val end=SystemClock.elapsedRealtime();Log.i("SkyTrailsRender","windowMs=${end-windowStart} freshConsumed=$consumed drawSubmitted=$draws cameraIntervalMeanMs=${if(intervals>0)intervalNs/intervals/1e6 else -1.0} renderCpuMeanMs=${if(renderCalls>0)cpuNs/renderCalls/1e6 else -1.0} blur=$blurEnabled fallback=${pipeline?.fallback ?: "none"} presentation=unmeasured");windowStart=end;consumed=0;draws=0;renderCalls=0;cpuNs=0;intervals=0;intervalNs=0;lastTimestamp=0}
 override fun onDetachedFromWindow() {
  dead=true;active=false;image.set(null);displays.unregisterDisplayListener(this)
  val old=input;input=null;controller.stop{old?.release();controller.release()}
  queueEvent{if(BuildConfig.DEBUG)telemetry();pipeline?.release();pipeline=null}
  super.onDetachedFromWindow()
 }
 private fun displayRotation()=when(display?.rotation){Surface.ROTATION_90->90;Surface.ROTATION_180->180;Surface.ROTATION_270->270;else->0}
 override fun onDisplayChanged(id:Int){rotation=displayRotation();version++;image.set(null);frameReady=false;requestRender()}
 override fun onDisplayAdded(id:Int){}
 override fun onDisplayRemoved(id:Int){}
}
