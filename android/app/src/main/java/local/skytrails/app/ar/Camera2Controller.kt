package local.skytrails.app.ar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import eu.domob.heliodos.BuildConfig
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlin.math.max

/** A renderer supplies ONE Surface. This controller owns/release that Surface; renderer owns texture.
 * All Camera2 resources live on one worker; every callback is admitted by CameraLifecycle.
 * UI receives immutable geometry, paired to renderer timestamps with geometryForFrame(). */
class Camera2Controller(context:Context) {
    private val app=context.applicationContext
    private val manager=app.getSystemService(CameraManager::class.java)
    private val thread=HandlerThread("SkyTrailsCamera").apply {start()}
    private val worker=Handler(thread.looper)
    private val main=Handler(Looper.getMainLooper())
    private val executor=Executor {worker.post(it)}
    private val gate=CameraLifecycle()
    private val frames=PreviewFrames<CaptureGeometry>()
    private val physicalFallback=PhysicalSetupFallback()
    private val owners=mutableSetOf<Owner>()
    private var active:Owner?=null
    private var pendingOpen:Owner?=null
    private var pinned:Pair<String,String>?=null
    private var rejectedPin=false
    private var released=false
    var onConfiguration:((Long,Size,Int?)->Unit)?=null
    var onState:((String)->Unit)?=null
    var onInvalidated:(()->Unit)?=null
    private var lastState=""
    private var lastDiagnostic=""
    data class Output(val size:Size,val sensorDegrees:Int?,val cameraId:String)
    private data class Selection(val id:String,val chars:CameraCharacteristics,val size:Size,val logical:Boolean)
    private class Owner(val epoch:Long,val selection:Selection,val surface:Surface,val physical:String?) {
        var camera:CameraDevice?=null
        var session:CameraCaptureSession?=null
        var closing=false
        var finished=false
        val completions=mutableListOf<()->Unit>()
        var probeId:String?=null
        var probeCount=0
    }
    /** Runs discovery off UI. configure creates the renderer Surface at the selected buffer size. */
    fun start(configure:(Output,(Surface?)->Unit)->Unit) {
        if(released)return
        val allowed=ContextCompat.checkSelfPermission(app,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
        val epoch=gate.start(true,allowed,true) ?: run {if(!allowed)state("相机权限未允许");return}
        worker.post {
            try {
                val s=select() ?: run {gate.fail(epoch);state("没有可用的后置相机");return@post}
                main.post configureOnMain@{
                    if(!gate.accepts(epoch))return@configureOnMain
                    configure(Output(s.size,s.chars.get(CameraCharacteristics.SENSOR_ORIENTATION),s.id)) {surface->
                        worker.post acceptSurface@{
                            if(surface==null) {gate.fail(epoch);return@acceptSurface}
                            if(!gate.accepts(epoch)) {surface.release();return@acceptSurface}
                            val owner=Owner(epoch,s,surface,pinned?.takeIf {it.first==s.id}?.second)
                            if(owners.isEmpty())open(owner) else pendingOpen=owner
                        }
                    }
                }
            } catch(e:Exception) {gate.fail(epoch);state("相机暂不可用：${e.javaClass.simpleName}")}
        }
    }
    fun stop(onStopped:()->Unit={}) {
        gate.stop();invalidateGeometry()
        worker.post {
            pendingOpen?.surface?.release();pendingOpen=null
            val pending=owners.toList()
            if(pending.isEmpty())main.post(onStopped)
            else {
                var remaining=pending.size
                pending.forEach {o->o.completions.add {remaining--;if(remaining==0)main.post(onStopped)};close(o)}
            }
            active=null
        }
    }
    fun release() {released=true;stop {thread.quitSafely()}}
    fun frameForTimestamp(timestampNs:Long):PreviewFrame<CaptureGeometry>? {
        val e=gate.epoch
        return if(gate.accepts(e)) frames.frame(e,timestampNs) else null
    }
    fun geometryForFrame(timestampNs:Long):CaptureGeometry? = frameForTimestamp(timestampNs)?.geometry
    private fun invalidateGeometry() {frames.clear();main.post {onInvalidated?.invoke()}}
    private fun state(message:String) {main.post {if(lastState!=message){lastState=message;onState?.invoke(message)}}}
    private fun select():Selection? {
        val candidates=manager.cameraIdList.mapNotNull {id->
            val c=manager.getCameraCharacteristics(id)
            val caps=c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            if(c.get(CameraCharacteristics.LENS_FACING)!=CameraCharacteristics.LENS_FACING_BACK || !caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE))return@mapNotNull null
            val map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return@mapNotNull null
            val sizes=map.getOutputSizes(SurfaceTexture::class.java)?.filter {max(it.width,it.height)<=1920} ?: emptyList()
            val size=sizes.filter {map.getOutputMinFrameDuration(SurfaceTexture::class.java,it) in 0..33_333_334L}.maxByOrNull {it.width*it.height}
                ?: sizes.maxByOrNull {it.width*it.height} ?: return@mapNotNull null
            Selection(id,c,size,caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA))
        }
        // CameraManager's first conventional rear camera is only a candidate. A logical 1x
        // probe establishes its active physical lens; no numeric ID or mm ranking means "main".
        return candidates.firstOrNull {it.logical} ?: candidates.firstOrNull()
    }
    private fun open(o:Owner) {
        owners.add(o);active=o
        try {
            if(ContextCompat.checkSelfPermission(app,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {gate.fail(o.epoch);finish(o);state("相机权限未允许");return}
            manager.openCamera(o.selection.id,object:CameraDevice.StateCallback() {
                override fun onOpened(camera:CameraDevice) {
                    o.camera=camera
                    if(o.closing || !gate.opened(o.epoch)) {close(o);return}
                    configure(o,camera)
                }
                override fun onDisconnected(camera:CameraDevice) {o.camera=camera;fail(o,"相机已断开")}
                override fun onError(camera:CameraDevice,error:Int) {o.camera=camera;fail(o,"相机错误，可退出后重试")}
                override fun onClosed(camera:CameraDevice) {finish(o)}
            },worker)
        } catch(e:Exception) {gate.fail(o.epoch);finish(o);state("相机无法打开：${e.javaClass.simpleName}")}
    }
    private fun configure(o:Owner,camera:CameraDevice) {
        try {
            val output=OutputConfiguration(o.surface)
            o.physical?.let {output.setPhysicalCameraId(it)}
            val config=SessionConfiguration(SessionConfiguration.SESSION_REGULAR,listOf(output),executor,object:CameraCaptureSession.StateCallback() {
                override fun onConfigured(session:CameraCaptureSession) {
                    if(!gate.configured(o.epoch) || o.closing) {session.close();close(o);return}
                    o.session=session
                    try {
                        val req=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        req.addTarget(o.surface); settings(req,o.selection.chars)
                        session.setRepeatingRequest(req.build(),capture(o),worker)
                        val sensor=(o.physical?.let {manager.getCameraCharacteristics(it)} ?: o.selection.chars).get(CameraCharacteristics.SENSOR_ORIENTATION)
                        main.post {if(gate.accepts(o.epoch)) onConfiguration?.invoke(o.epoch,o.selection.size,sensor)}
                        state(if(o.selection.logical && o.physical==null) "正在核验后置 1× 镜头" else "相机已启动，等待匹配帧参数")
                    } catch(e:Exception) {fail(o,"相机请求失败：${e.javaClass.simpleName}")}
                }
                override fun onConfigureFailed(session:CameraCaptureSession) {
                    session.close()
                    if(!recoverPhysicalSetup(o,null))fail(o,"镜头会话配置失败，可退出后重试")
                }
            })
            camera.createCaptureSession(config)
        } catch(e:Exception) {
            if(!recoverPhysicalSetup(o,e))fail(o,"相机配置失败：${e.javaClass.simpleName}")
        }
    }
    private fun recoverPhysicalSetup(o:Owner,failure:Throwable?):Boolean {
        val permission=ContextCompat.checkSelfPermission(app,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
        val ticket=physicalFallback.reject(gate,o.epoch,o.physical!=null,permission,failure) ?: return false
        pinned=null;rejectedPin=true;invalidateGeometry()
        o.completions.add {main.post {if(ticket.isCurrent(gate))onReconfigure?.invoke()}}
        close(o);state("固定镜头不可用，恢复普通预览")
        return true
    }
    private fun settings(b:CaptureRequest.Builder,c:CameraCharacteristics) {
        val keys=c.availableCaptureRequestKeys
        fun <T> set(k:CaptureRequest.Key<T>,v:T) {if(keys.contains(k))b.set(k,v)}
        if(c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)==true)set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.takeIf {it.contains(1f)}?.let {set(CaptureRequest.CONTROL_ZOOM_RATIO,1f)}
        val active=c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        if(active!=null)set(CaptureRequest.SCALER_CROP_REGION,Rect(0,0,active.width(),active.height()))
        c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.filter {it.contains(30)}?.minByOrNull {abs(30-it.lower)+abs(it.upper-30)}?.let {set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,it)}
        set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
        if(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF)==true)set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF)
        val modes=c.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES)
        if(modes?.contains(CaptureRequest.DISTORTION_CORRECTION_MODE_FAST)==true)set(CaptureRequest.DISTORTION_CORRECTION_MODE,CaptureRequest.DISTORTION_CORRECTION_MODE_FAST)
        else if(modes?.contains(CaptureRequest.DISTORTION_CORRECTION_MODE_OFF)==true)set(CaptureRequest.DISTORTION_CORRECTION_MODE,CaptureRequest.DISTORTION_CORRECTION_MODE_OFF)
        if(Build.VERSION.SDK_INT>=31 && c.get(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES)?.contains(CaptureRequest.SCALER_ROTATE_AND_CROP_NONE)==true)set(CaptureRequest.SCALER_ROTATE_AND_CROP,CaptureRequest.SCALER_ROTATE_AND_CROP_NONE)
    }
    private fun capture(o:Owner)=object:CameraCaptureSession.CaptureCallback() {
        override fun onCaptureStarted(session:CameraCaptureSession,request:CaptureRequest,timestamp:Long,frameNumber:Long) {
            if(!gate.accepts(o.epoch) || o.closing)return
            // Camera2 documents this timestamp as matching output buffers (including SurfaceTexture).
            // Freshness does not require the optional per-physical-camera CaptureResult.
            frames.recordFrame(o.epoch,timestamp)
        }
        override fun onCaptureCompleted(session:CameraCaptureSession,request:CaptureRequest,result:TotalCaptureResult) {
            if(!gate.accepts(o.epoch) || o.closing)return
            result.get(CaptureResult.SENSOR_TIMESTAMP)?.let {frames.recordFrame(o.epoch,it)}
            val activePhysical=result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)
            if(o.selection.logical && o.physical==null && !rejectedPin && activePhysical!=null && activePhysical in o.selection.chars.physicalCameraIds && result.get(CaptureResult.CONTROL_ZOOM_RATIO)==1f) {
                o.probeCount=if(o.probeId==activePhysical)o.probeCount+1 else 1; o.probeId=activePhysical
                if(o.probeCount==3) {
                    try {
                        val pc=manager.getCameraCharacteristics(activePhysical)
                        val sizes=pc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(SurfaceTexture::class.java)
                        if(sizes?.contains(o.selection.size)==true) {
                            pinned=o.selection.id to activePhysical
                            // Same device/surface; configure physical output only after closing old session.
                            o.session?.close();o.session=null
                            gate.stop();invalidateGeometry()
                            val stoppedEpoch=gate.epoch
                            // Transfer surface to replacement; original camera must be closed before reopen.
                            o.completions.add {main.post {if(gate.epoch==stoppedEpoch)onReconfigure?.invoke()}}
                            close(o)
                            state("已识别 1× 镜头，正在固定输出")
                            return
                        }
                        rejectedPin=true
                    } catch(e:Exception) {rejectedPin=true}
                }
            }
            val metadataId=o.physical ?: o.selection.id
            val source:CaptureResult=if(o.physical!=null) {
                if(Build.VERSION.SDK_INT>=31)result.physicalCameraTotalResults[o.physical] ?: return
                else result.physicalCameraResults[o.physical] ?: return
            } else result
            val chars=try {if(o.physical!=null)manager.getCameraCharacteristics(o.physical) else o.selection.chars} catch(e:CameraAccessException){return}
            val timestamp=source.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
            val distortion=source.get(CaptureResult.DISTORTION_CORRECTION_MODE) ?: result.get(CaptureResult.DISTORTION_CORRECTION_MODE)
            val active=if(distortion==CaptureResult.DISTORTION_CORRECTION_MODE_OFF) chars.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE) ?: chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) else chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            val physical=chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val pixels=chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            val actualFocal=source.get(CaptureResult.LENS_FOCAL_LENGTH)
            val staticFocal=chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.singleOrNull()
            val snapshot=CaptureGeometry(o.epoch,timestamp,o.selection.id,o.physical?:activePhysical,metadataId,
                if(o.physical!=null)LensLock.PHYSICAL_OUTPUT else if(o.selection.logical)LensLock.UNLOCKED_LOGICAL else LensLock.DIRECT_REAR,
                chars.get(CameraCharacteristics.SENSOR_ORIENTATION),o.selection.size.width,o.selection.size.height,
                OpticsMetadata((actualFocal?:staticFocal)?.toDouble(),physical?.width?.toDouble(),physical?.height?.toDouble(),pixels?.width,pixels?.height,active?.asRect()),
                source.get(CaptureResult.SCALER_CROP_REGION)?.asRect(),source.get(CaptureResult.CONTROL_ZOOM_RATIO),distortion,
                if(Build.VERSION.SDK_INT>=31) result.get(CaptureResult.SCALER_ROTATE_AND_CROP) else 0,
                result.get(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE),source.get(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE) ?: result.get(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE),
                chars.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE),RawCalibration(source.get(CaptureResult.LENS_INTRINSIC_CALIBRATION)?:chars.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION),source.get(CaptureResult.LENS_DISTORTION)?:chars.get(CameraCharacteristics.LENS_DISTORTION)),if(actualFocal!=null)"capture" else "single static focal")
            frames.recordFrame(o.epoch,timestamp)
            frames.putGeometry(o.epoch,timestamp,snapshot)
            val diag="camera=${snapshot.cameraId}; physical=${snapshot.physicalId}; metadata=${snapshot.metadataCameraId}; lock=${snapshot.lock}; stream=${snapshot.streamWidth}x${snapshot.streamHeight}; S=${snapshot.sensorDegrees}; active=${snapshot.optics.active}; crop=${snapshot.crop}; focal=${snapshot.optics.focalMm}; focalSource=${snapshot.focalSource}; zoom=${snapshot.zoomRatio}; distortion=${snapshot.distortionMode}; rotateCrop=${snapshot.rotateAndCrop}; videoStab=${snapshot.videoStabilization}; ois=${snapshot.opticalStabilization}; timestampSource=${snapshot.timestampSource}; intrinsicsPresent=${snapshot.rawCalibration.intrinsics()!=null}; quality=${snapshot.quality}"
            if(BuildConfig.DEBUG && diag!=lastDiagnostic){lastDiagnostic=diag;Log.i("SkyTrailsCamera",diag+"; rawIntrinsicsSample=${snapshot.rawCalibration.intrinsics()?.contentToString()}; rawDistortionSample=${snapshot.rawCalibration.distortion()?.contentToString()}")}
        }
    }
    var onReconfigure:(()->Unit)?=null
    private fun Rect.asRect()=RectD(left.toDouble(),top.toDouble(),width().toDouble(),height().toDouble())
    private fun fail(o:Owner,message:String) {if(gate.accepts(o.epoch)){gate.fail(o.epoch);invalidateGeometry();state(message)};close(o)}
    private fun close(o:Owner) {
        if(o.finished)return
        o.closing=true
        try{o.session?.stopRepeating()}catch(_:Exception){}
        o.session?.close();o.session=null
        o.camera?.close()
        // An opening device still owns the surface until its late onOpened/onClosed arrives.
    }
    private fun finish(o:Owner) {
        if(o.finished)return
        o.finished=true;owners.remove(o);if(active===o)active=null
        o.surface.release();o.completions.forEach {it()}
        if(owners.isEmpty()) {
            val next=pendingOpen;pendingOpen=null
            if(next!=null) {
                if(gate.accepts(next.epoch))open(next) else next.surface.release()
            }
        }
    }
}
