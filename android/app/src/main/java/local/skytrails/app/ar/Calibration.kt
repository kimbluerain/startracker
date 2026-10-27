package local.skytrails.app.ar

import java.time.Instant
import kotlin.math.*

data class CalibrationKey(val cameraId:String,val physicalId:String?,val sensorDegrees:Int,val displayDegrees:Int,val streamWidth:Int,val streamHeight:Int,val opticsFingerprint:String)
/** Scene yaw about camera-up, then pitch about camera-right. Positive moves scene right/up. */
data class ManualOffset(val yawDegrees:Double=0.0,val pitchDegrees:Double=0.0) {
    init {require(yawDegrees.isFinite() && pitchDegrees.isFinite())}
    fun applyForDisplay(v:CameraVector,displayDegrees:Int):CameraVector =
        deviceCameraToSensor(apply(deviceCameraToSensor(v,displayDegrees)),(360-displayDegrees)%360)
    fun apply(v:CameraVector):CameraVector {
        fun radians(d:Double)=Math.toRadians(((d+180)%360+360)%360-180)
        val y=radians(yawDegrees); val p=radians(pitchDegrees)
        val x=cos(y)*v.xRight+sin(y)*v.zForward
        val z=-sin(y)*v.xRight+cos(y)*v.zForward
        return CameraVector(x,cos(p)*v.yUp+sin(p)*z,-sin(p)*v.yUp+cos(p)*z)
    }
}
enum class CalibrationStatus { PENDING, APPLIED, EXPIRED }
data class CalibrationState(val status:CalibrationStatus,val offset:ManualOffset=ManualOffset())
/** Session-only directional calibration. Never promotes estimated lens intrinsics to calibrated. */
class CalibrationStore {
    private data class Entry(val key:CalibrationKey,val site:PhysicalSite,val at:Instant,val offset:ManualOffset)
    private var entry:Entry?=null
    private var expired=false
    fun set(key:CalibrationKey,site:PhysicalSite,now:Instant,offset:ManualOffset) {entry=Entry(key,site,now,offset); expired=false}
    fun clear() {entry=null; expired=false}
    fun resolve(key:CalibrationKey,site:PhysicalSite,now:Instant):CalibrationState {
        val e=entry ?: return CalibrationState(CalibrationStatus.PENDING)
        val age=now.epochSecond-e.at.epochSecond
        if(e.key!=key || distanceMeters(e.site,site)>1000 || age !in 0..86400) expired=true
        return if(expired) CalibrationState(CalibrationStatus.EXPIRED) else CalibrationState(CalibrationStatus.APPLIED,e.offset)
    }
    private fun distanceMeters(a:PhysicalSite,b:PhysicalSite):Double {
        val lat1=Math.toRadians(a.latitude); val lat2=Math.toRadians(b.latitude)
        val dlat=lat2-lat1; val dlon=Math.toRadians(b.longitude-a.longitude)
        return 6371000*2*asin(sqrt((sin(dlat/2).pow(2)+cos(lat1)*cos(lat2)*sin(dlon/2).pow(2)).coerceIn(0.0,1.0)))
    }
}
