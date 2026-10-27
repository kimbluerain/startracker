package local.skytrails.app.ui.sky

import local.skytrails.app.ar.CameraVector
import kotlin.math.*

data class TextureCoordinate(val u:Double,val v:Double)
/** Orthogonal Galactic axes expressed through the same camera orientation as the overlay. */
data class GalacticCameraBasis(val x:CameraVector,val y:CameraVector,val z:CameraVector) {
 fun coordinate(ray:CameraVector):TextureCoordinate {
  fun dot(a:CameraVector)=a.xRight*ray.xRight+a.yUp*ray.yUp+a.zForward*ray.zForward
  val gx=dot(x);val gy=dot(y);val gz=dot(z);val length=sqrt(gx*gx+gy*gy+gz*gz)
  val longitude=atan2(gy,gx)
  // NASA SVS 4851, Galactic plate carrée: l=0 center, increasing l left, north up.
  val u=((.5-longitude/(2*PI))%1+1)%1
  return TextureCoordinate(u,.5-asin((gz/length).coerceIn(-1.0,1.0))/PI)
 }
 companion object {
  fun from(projection:SkyProjection,directions:List<local.skytrails.app.core.SkyDirection>):GalacticCameraBasis? {
   if(directions.size!=3)return null
   return GalacticCameraBasis(projection.toCamera(directions[0])?:return null,projection.toCamera(directions[1])?:return null,projection.toCamera(directions[2])?:return null)
  }
  /** Unlocated decorative view. It carries no site, labels or claim of physical orientation. */
  val UNLOCATED=GalacticCameraBasis(CameraVector(0.0,0.0,1.0),CameraVector(-.8,.6,0.0),CameraVector(.6,.8,0.0))
 }
}

/** Visual exposure only; never used by numerical positions or visibility qualification. */
fun composeSkyPixel(star:Int,diffuse:Int,galaxyVisible:Boolean,aboveHorizonFactor:Double,night:Boolean,exposure:Double):Int {
 fun channel(shift:Int,base:Int):Int {
  val stars=(star ushr shift)and 255;val cloud=if(galaxyVisible)(diffuse ushr shift)and 255 else 0
  return (base+(stars*.95+cloud*1.05)*aboveHorizonFactor*exposure).toInt().coerceIn(0,255)
 }
 var r=channel(16,5);var g=channel(8,10);var b=channel(0,18)
 if(night){r=(r*.7+g*.15+b*.15).toInt();g=(r*.15).toInt();b=(r*.09).toInt()}
 return (255 shl 24)or(r shl 16)or(g shl 8)or b
}

/** Photographic fade around the exact, separately drawn 0-degree geometric horizon. */
fun horizonBackdropFactor(altitudeDeg:Double):Double {
 val t=((altitudeDeg+4.0)/8.0).coerceIn(0.0,1.0)
 return .12+.88*t*t*(3-2*t)
}
