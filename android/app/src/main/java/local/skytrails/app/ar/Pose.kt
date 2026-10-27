package local.skytrails.app.ar

import java.time.Instant
import local.skytrails.app.core.SkyDirection
import kotlin.math.*

/** Physical measurement only; a selected remote city must never enter this type. */
data class PhysicalSite(val latitude:Double,val longitude:Double,val altitudeMeters:Double) {
    init { require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0 && altitudeMeters.isFinite()) }
}
fun interface DeclinationProvider { fun degrees(site:PhysicalSite,realInstant:Instant):Double }
/** Pure orientation shared by physical sensors and explicitly synthetic virtual views. */
class SkyOrientation(matrix:DoubleArray,private val declinationDegrees:Double=0.0) {
    private val r=matrix.copyOf()
    init { require(validRotation(r) && declinationDegrees.isFinite()) }
    fun toCamera(direction:SkyDirection):CameraVector? {
        if(!direction.azimuthDeg.isFinite() || !direction.altitudeDeg.isFinite() || direction.altitudeDeg !in -90.0..90.0) return null
        // True azimuth minus east declination is magnetic azimuth. At +10° declination,
        // true north is to the LEFT of a camera looking at magnetic north.
        val az=Math.toRadians(direction.azimuthDeg-declinationDegrees)
        val alt=Math.toRadians(direction.altitudeDeg)
        val e=sin(az)*cos(alt); val n=cos(az)*cos(alt); val u=sin(alt)
        return CameraVector(r[0]*e+r[3]*n+r[6]*u,r[1]*e+r[4]*n+r[7]*u,-(r[2]*e+r[5]*n+r[8]*u))
    }
    fun fromCamera(vector:CameraVector):SkyDirection? {
        val x=vector.xRight;val y=vector.yUp;val z=-vector.zForward
        val length=sqrt(x*x+y*y+z*z)
        if(!length.isFinite() || length==0.0)return null
        val e=r[0]*x+r[1]*y+r[2]*z;val n=r[3]*x+r[4]*y+r[5]*z;val u=r[6]*x+r[7]*y+r[8]*z
        return SkyDirection((Math.toDegrees(atan2(e,n))+declinationDegrees+720)%360,Math.toDegrees(asin((u/length).coerceIn(-1.0,1.0))))
    }
    companion object {
        /** Synthetic level-roll camera, aimed by true azimuth/altitude; no physical-site claim. */
        fun virtualView(azimuthDeg:Double,altitudeDeg:Double):SkyOrientation {
            require(azimuthDeg.isFinite() && altitudeDeg.isFinite() && altitudeDeg in -90.0..90.0)
            val a=Math.toRadians(azimuthDeg); val h=Math.toRadians(altitudeDeg)
            // Columns: camera right, up, back in true ENU. Only defines a virtual view.
            return SkyOrientation(doubleArrayOf(cos(a),-sin(a)*sin(h),-sin(a)*cos(h),
                -sin(a),-cos(a)*sin(h),-cos(a)*cos(h),0.0,cos(h),-sin(h)))
        }
        internal fun validRotation(r:DoubleArray):Boolean {
            if(r.size!=9 || !r.all {it.isFinite()}) return false
            for(i in 0..2) for(j in 0..2) {
                val dot=(0..2).sumOf {k->r[i*3+k]*r[j*3+k]}
                if(abs(dot-(if(i==j)1.0 else 0.0))>0.01) return false
            }
            val det=r[0]*(r[4]*r[8]-r[5]*r[7])-r[1]*(r[3]*r[8]-r[5]*r[6])+r[2]*(r[3]*r[7]-r[4]*r[6])
            return abs(det-1.0)<0.01
        }
    }
}
/** SensorManager R maps natural device axes to magnetic ENU. No display remapping here. */
class PoseFrame(val site:PhysicalSite,val realInstant:Instant,matrix:DoubleArray,val declinationDegrees:Double) {
    private val orientation=SkyOrientation(matrix,declinationDegrees)
    fun toCamera(direction:SkyDirection):CameraVector? = orientation.toCamera(direction)
    fun fromCamera(vector:CameraVector):SkyDirection? = orientation.fromCamera(vector)
    companion object {
        fun capture(site:PhysicalSite?,now:Instant,matrix:DoubleArray,provider:DeclinationProvider):PoseFrame? {
            if(site==null || !SkyOrientation.validRotation(matrix)) return null
            val d=provider.degrees(site,now)
            return if(d.isFinite()) PoseFrame(site,now,matrix,d) else null
        }
    }
}
/** Undo sensor mounting in image xy-down axes; output retains the right/up/forward API. */
fun deviceCameraToSensor(v:CameraVector,s:Int):CameraVector = when(s) {
    0 -> v
    90 -> CameraVector(-v.yUp,v.xRight,v.zForward)
    180 -> CameraVector(-v.xRight,-v.yUp,v.zForward)
    270 -> CameraVector(v.yUp,-v.xRight,v.zForward)
    else -> error("Unknown sensor orientation")
}
