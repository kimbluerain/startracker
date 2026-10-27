package local.skytrails.app.ui.sky

import local.skytrails.app.ar.*
import local.skytrails.app.core.*
import kotlin.math.*

enum class GalacticLine { PLANE, BAND_EDGE }
/** Draw each segment independently; belowHorizon means dashed, never connect separate segments. */
data class SkySegment(val start: Pixel, val end: Pixel, val belowHorizon: Boolean, val line: GalacticLine)
sealed interface TargetIndicator {
    data class Visible(val pixel: Pixel, val belowHorizon: Boolean): TargetIndicator
    data class Edge(val pixel: Pixel, val angleRadians: Double, val belowHorizon: Boolean): TargetIndicator
    /** Host shows "目标在身后，请转身"; intentionally has no invented front-screen position. */
    data class Behind(val belowHorizon: Boolean): TargetIndicator
    data object Unavailable: TargetIndicator
}
data class GalacticDrawing(val segments: List<SkySegment>, val center: TargetIndicator)

/** One projection contract for vectors and clipped paths. Camera optics retain Task3 qualification. */
class SkyProjection private constructor(
    val width:Int,val height:Int,
    internal val toCamera:(SkyDirection)->CameraVector?,
    internal val toPixel:(CameraVector)->Pixel?
) {
    companion object {
        /** Synthetic focal length defines a virtual FOV; never used as physical camera metadata. */
        fun virtual(width:Int,height:Int,horizontalFovDeg:Double,azimuthDeg:Double,altitudeDeg:Double):SkyProjection {
            require(width>0 && height>0 && horizontalFovDeg.isFinite() && horizontalFovDeg in 1.0..170.0)
            val orientation=SkyOrientation.virtualView(azimuthDeg,altitudeDeg)
            val f=width/(2*tan(Math.toRadians(horizontalFovDeg)/2))
            val k=Intrinsics(f,f,width/2.0,height/2.0)
            return SkyProjection(width,height,orientation::toCamera) {project(it,k)}
        }
        /** Synthetic FOV for the virtual sky, with the full measured true-north/roll chain. */
        fun sensor(width:Int,height:Int,horizontalFovDeg:Double,pose:PoseFrame,displayDegrees:Int,offset:ManualOffset=ManualOffset()):SkyProjection {
            require(width>0 && height>0 && horizontalFovDeg.isFinite() && horizontalFovDeg in 1.0..170.0)
            require(displayDegrees in listOf(0,90,180,270))
            val f=width/(2*tan(Math.toRadians(horizontalFovDeg)/2));val k=Intrinsics(f,f,width/2.0,height/2.0)
            return SkyProjection(width,height,{direction->pose.toCamera(direction)?.let{offset.apply(deviceCameraToSensor(it,displayDegrees))}},{project(it,k)})
        }
        fun manual(width:Int,height:Int,horizontalFovDeg:Double,basis:SkyViewBasis):SkyProjection {
            require(width>0 && height>0 && horizontalFovDeg.isFinite() && horizontalFovDeg in 1.0..170.0)
            val f=width/(2*tan(Math.toRadians(horizontalFovDeg)/2));val k=Intrinsics(f,f,width/2.0,height/2.0)
            return SkyProjection(width,height,basis::toCamera){project(it,k)}
        }
        fun camera(pose:PoseFrame,capture:CaptureGeometry,preview:PreviewGeometry,offset:ManualOffset=ManualOffset()):SkyProjection? {
            // This calls the existing qualification guard, including epoch, sensor and stream matching.
            if(capture.projectNatural(CameraVector(0.0,0.0,1.0),preview)==null) return null
            return SkyProjection(preview.viewWidth,preview.viewHeight,
                {direction->pose.toCamera(direction)?.let {offset.applyForDisplay(it,preview.displayDegrees)}},
                {v->capture.projectNatural(v,preview)})
        }
    }
}

/** Pure rendering geometry for Canvas/Compose or offline SVG. No Android view or second pose formula. */
object GalacticOverlay {
    private const val NEAR=1e-7

    fun render(sky:GalacticSky,projection:SkyProjection)=GalacticDrawing(
        path(sky.plane,projection)+path(sky.bandSouth,projection,GalacticLine.BAND_EDGE)+
            path(sky.bandNorth,projection,GalacticLine.BAND_EDGE),target(sky.center,projection))

    fun path(points:List<SkyDirection>,projection:SkyProjection,line:GalacticLine=GalacticLine.PLANE):List<SkySegment> {
        val result=mutableListOf<SkySegment>()
        for((a,b) in points.zipWithNext()) {
            val ca=projection.toCamera(a) ?: continue
            val cb=projection.toCamera(b) ?: continue
            // Antipodal endpoints have no unique short arc; never create a through-origin chord.
            if(squaredLength(lerp(ca,cb,0.5))<1e-12) continue
            val ua=sin(Math.toRadians(a.altitudeDeg)); val ub=sin(Math.toRadians(b.altitudeDeg))
            if(ua*ub<0.0) {
                val horizon=lerp(ca,cb,ua/(ua-ub))
                segment(ca,horizon,ua<0,projection,line)?.let(result::add)
                segment(horizon,cb,ub<0,projection,line)?.let(result::add)
            } else segment(ca,cb,ua+ub<0,projection,line)?.let(result::add)
        }
        return result
    }

    fun target(direction:SkyDirection,projection:SkyProjection):TargetIndicator {
        val v=projection.toCamera(direction) ?: return TargetIndicator.Unavailable
        val below=direction.altitudeDeg<0
        if(v.zForward<=NEAR) return TargetIndicator.Behind(below)
        val p=projection.toPixel(v) ?: return TargetIndicator.Unavailable
        if(p.x in 0.0..projection.width.toDouble() && p.y in 0.0..projection.height.toDouble()) return TargetIndicator.Visible(p,below)
        val cx=projection.width/2.0; val cy=projection.height/2.0
        val dx=p.x-cx; val dy=p.y-cy
        val inset=min(12.0,min(cx,cy)/2)
        val scale=min(if(dx==0.0)Double.POSITIVE_INFINITY else (cx-inset)/abs(dx),
            if(dy==0.0)Double.POSITIVE_INFINITY else (cy-inset)/abs(dy))
        return TargetIndicator.Edge(Pixel(cx+scale*dx,cy+scale*dy),atan2(dy,dx),below)
    }

    private fun segment(start:CameraVector,end:CameraVector,below:Boolean,projection:SkyProjection,line:GalacticLine):SkySegment? {
        if(start.zForward<NEAR && end.zForward<NEAR) return null
        var a=start; var b=end
        // Clip in 3D BEFORE dividing by depth. Both back-facing endpoints never project.
        if(a.zForward<NEAR) a=lerp(start,end,(NEAR-start.zForward)/(end.zForward-start.zForward))
        if(b.zForward<NEAR) b=lerp(start,end,(NEAR-start.zForward)/(end.zForward-start.zForward))
        val pa=projection.toPixel(a) ?: return null
        val pb=projection.toPixel(b) ?: return null
        val clipped=clipViewport(pa,pb,projection.width.toDouble(),projection.height.toDouble()) ?: return null
        if(hypot(clipped.first.x-clipped.second.x,clipped.first.y-clipped.second.y)<1e-8) return null
        return SkySegment(clipped.first,clipped.second,below,line)
    }

    /** Clip the perspective-projected segment to all four viewport planes. */
    private fun clipViewport(a:Pixel,b:Pixel,width:Double,height:Double):Pair<Pixel,Pixel>? {
        var enter=0.0; var leave=1.0
        for((origin,delta,limit) in listOf(Triple(a.x,b.x-a.x,width),Triple(a.y,b.y-a.y,height))) {
            if(delta==0.0) { if(origin<0 || origin>limit) return null }
            else {
                val t0=-origin/delta; val t1=(limit-origin)/delta
                enter=max(enter,min(t0,t1)); leave=min(leave,max(t0,t1))
                if(enter>leave) return null
            }
        }
        fun at(t:Double)=Pixel((a.x+(b.x-a.x)*t).coerceIn(0.0,width),(a.y+(b.y-a.y)*t).coerceIn(0.0,height))
        return at(enter) to at(leave)
    }
    private fun lerp(a:CameraVector,b:CameraVector,t:Double)=CameraVector(
        a.xRight+(b.xRight-a.xRight)*t,a.yUp+(b.yUp-a.yUp)*t,a.zForward+(b.zForward-a.zForward)*t)
    private fun squaredLength(v:CameraVector)=v.xRight*v.xRight+v.yUp*v.yUp+v.zForward*v.zForward
}
