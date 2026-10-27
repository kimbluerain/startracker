package local.skytrails.app.ar

import kotlin.math.max

/** Camera axes: right, up, forward. Sensor image axes are right, down. */
data class CameraVector(val xRight: Double, val yUp: Double, val zForward: Double)
data class Pixel(val x: Double, val y: Double)
data class Intrinsics(val fx: Double, val fy: Double, val cx: Double, val cy: Double)
data class RectD(val left: Double, val top: Double, val width: Double, val height: Double) {
    val valid get() = listOf(left, top, width, height).all { it.isFinite() } && width > 0 && height > 0
}
fun project(v: CameraVector, k: Intrinsics): Pixel? {
    if (!listOf(v.xRight,v.yUp,v.zForward,k.fx,k.fy,k.cx,k.cy).all { it.isFinite() } || v.zForward <= 0 || k.fx <= 0 || k.fy <= 0) return null
    val p = Pixel(k.fx*v.xRight/v.zForward+k.cx, k.cy-k.fy*v.yUp/v.zForward)
    return p.takeIf { it.x.isFinite() && it.y.isFinite() }
}
fun streamCrop(c: RectD, w: Int, h: Int): RectD? {
    if (!c.valid || w <= 0 || h <= 0) return null
    val ratio = w.toDouble()/h
    return if(c.width/c.height > ratio) RectD(c.left+(c.width-c.height*ratio)/2,c.top,c.height*ratio,c.height)
    else RectD(c.left,c.top+(c.height-c.width/ratio)/2,c.width,c.width/ratio)
}
fun activeToStream(p: Pixel, c: RectD, w: Int, h: Int): Pixel? {
    if(!p.x.isFinite() || !p.y.isFinite()) return null
    val e=streamCrop(c,w,h) ?: return null
    return Pixel((p.x-e.left)*w/e.width,(p.y-e.top)*h/e.height)
}
/** Real metadata, with explicitly estimated centered principal point. Never calibrated optics. */
data class OpticsMetadata(val focalMm: Double?, val physicalWidthMm: Double?, val physicalHeightMm: Double?, val pixelWidth: Int?, val pixelHeight: Int?, val active: RectD?) {
    fun estimate(): Intrinsics? {
        val f=focalMm ?: return null; val pw=physicalWidthMm ?: return null; val ph=physicalHeightMm ?: return null
        val w=pixelWidth ?: return null; val h=pixelHeight ?: return null; val a=active ?: return null
        if(!listOf(f,pw,ph).all { it.isFinite() && it>0 } || w<=0 || h<=0 || !a.valid || a.left<0 || a.top<0 || a.left+a.width>w || a.top+a.height>h) return null
        return Intrinsics(f*w/pw,f*h/ph,w/2.0-a.left,h/2.0-a.top)
    }
}
/** Immutable row-major affine 3x3, column-vector convention; a*b applies b then a. */
class Mat3 private constructor(private val a: DoubleArray) {
    fun values() = a.copyOf()
    fun map(p: Pixel) = Pixel(a[0]*p.x+a[1]*p.y+a[2],a[3]*p.x+a[4]*p.y+a[5])
    operator fun times(b: Mat3): Mat3 = Mat3(DoubleArray(9) { i -> (0..2).sumOf { j -> a[i/3*3+j]*b.a[j*3+i%3] } })
    fun inverse(): Mat3 {
        val det=a[0]*a[4]-a[1]*a[3]; require(det.isFinite() && det!=0.0)
        return of(a[4]/det,-a[1]/det,(a[1]*a[5]-a[4]*a[2])/det,-a[3]/det,a[0]/det,(a[3]*a[2]-a[0]*a[5])/det)
    }
    companion object {
        fun of(a:Double,b:Double,c:Double,d:Double,e:Double,f:Double)=Mat3(doubleArrayOf(a,b,c,d,e,f,0.0,0.0,1.0))
        fun scaleTranslate(sx:Double,sy:Double,x:Double=0.0,y:Double=0.0)=of(sx,0.0,x,0.0,sy,y)
        fun rotation(degrees:Int,w:Int,h:Int): Mat3 = when(degrees) {
            0 -> of(1.0,0.0,0.0,0.0,1.0,0.0)
            90 -> of(0.0,-1.0,h.toDouble(),1.0,0.0,0.0)
            180 -> of(-1.0,0.0,w.toDouble(),0.0,-1.0,h.toDouble())
            270 -> of(0.0,1.0,0.0,-1.0,0.0,w.toDouble())
            else -> error("Rotation must be 0/90/180/270")
        }
    }
}
data class PreviewGeometry(val epoch:Long, val sensorDegrees:Int, val version:Long, val streamWidth:Int, val streamHeight:Int, val viewWidth:Int, val viewHeight:Int, val displayDegrees:Int, val streamToView:Mat3, val viewToStream:Mat3, val textureTransform:Mat3) {
    companion object {
        fun create(version:Long,w:Int,h:Int,vw:Int,vh:Int,s:Int,d:Int,epoch:Long=0): PreviewGeometry {
            require(w>0 && h>0 && vw>0 && vh>0 && s in listOf(0,90,180,270) && d in listOf(0,90,180,270))
            val theta=(s-d+360)%360; val rw=if(theta%180==0)w else h; val rh=if(theta%180==0)h else w
            val q=max(vw.toDouble()/rw,vh.toDouble()/rh)
            val g=Mat3.scaleTranslate(q,q,(vw-q*rw)/2,(vh-q*rh)/2)*Mat3.rotation(theta,w,h)
            return PreviewGeometry(epoch,s,version,w,h,vw,vh,d,g,g.inverse(),g*textureDefault(w,h,vw,vh,s).inverse())
        }
        fun textureDefault(w:Int,h:Int,vw:Int,vh:Int,s:Int):Mat3 {
            val rw=if(s%180==0)w else h; val rh=if(s%180==0)h else w
            return Mat3.scaleTranslate(vw.toDouble()/rw,vh.toDouble()/rh)*Mat3.rotation(s,w,h)
        }
    }
}
/** Exact capture timestamp + epoch pairing. Never reuse metadata from a different camera frame. */
class FrameGeometryCache<T>(private val capacity:Int=12) {
    init { require(capacity>0) }
    private val frames=linkedMapOf<Pair<Long,Long>,T>()
    @Synchronized fun put(epoch:Long,timestamp:Long,value:T) {
        frames[epoch to timestamp]=value
        while(frames.size>capacity) frames.remove(frames.keys.first())
    }
    @Synchronized fun get(epoch:Long,timestamp:Long):T? = frames[epoch to timestamp]
    @Synchronized fun clear() { frames.clear() }
}
