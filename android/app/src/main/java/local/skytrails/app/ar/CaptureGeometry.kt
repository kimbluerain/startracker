package local.skytrails.app.ar

/** Arrays from Camera2 are copied on both ingress and egress. They are evidence, not yet a calibrated model. */
class RawCalibration(intrinsics:FloatArray?,distortion:FloatArray?) {
    private val k=intrinsics?.copyOf(); private val d=distortion?.copyOf()
    fun intrinsics()=k?.copyOf()
    fun distortion()=d?.copyOf()
}
enum class LensLock { DIRECT_REAR, PHYSICAL_OUTPUT, UNLOCKED_LOGICAL }
enum class OpticsQuality { UNAVAILABLE, METADATA_ESTIMATE }
data class CaptureGeometry(
    val epoch:Long, val timestampNs:Long, val cameraId:String, val physicalId:String?,
    val metadataCameraId:String, val lock:LensLock, val sensorDegrees:Int?,
    val streamWidth:Int, val streamHeight:Int, val optics:OpticsMetadata,
    val crop:RectD?, val zoomRatio:Float?, val distortionMode:Int?,
    val rotateAndCrop:Int?, val videoStabilization:Int?, val opticalStabilization:Int?,
    val timestampSource:Int?, val rawCalibration:RawCalibration,
    val focalSource:String
) {
    val quality:OpticsQuality get() {
        val expected=if(lock==LensLock.PHYSICAL_OUTPUT)physicalId else cameraId
        val a=optics.active; val c=crop
        return if(lock==LensLock.UNLOCKED_LOGICAL || expected!=metadataCameraId || sensorDegrees !in listOf(0,90,180,270) || optics.estimate()==null || a==null || c==null || !c.valid || c.left<0 || c.top<0 || c.left+c.width>a.width+0.001 || c.top+c.height>a.height+0.001 || streamWidth<=0 || streamHeight<=0 || (zoomRatio!=null && zoomRatio!=1f) || rotateAndCrop!=0 || videoStabilization!=0 || opticalStabilization!=0 || distortionMode==null) OpticsQuality.UNAVAILABLE else OpticsQuality.METADATA_ESTIMATE
    }
    fun calibrationKey(preview:PreviewGeometry)=CalibrationKey(cameraId,physicalId,sensorDegrees?:-1,preview.displayDegrees,streamWidth,streamHeight,"${optics.focalMm}/${optics.active}/$crop/$zoomRatio/$distortionMode/$rotateAndCrop/$lock")
    fun projectNatural(v:CameraVector,preview:PreviewGeometry):Pixel? {
        if(quality==OpticsQuality.UNAVAILABLE || preview.epoch!=epoch || preview.sensorDegrees!=sensorDegrees || preview.streamWidth!=streamWidth || preview.streamHeight!=streamHeight)return null
        val raw=project(deviceCameraToSensor(v,sensorDegrees!!),optics.estimate()!!) ?: return null
        val buffer=activeToStream(raw,crop!!,streamWidth,streamHeight) ?: return null
        return preview.streamToView.map(buffer)
    }
}
