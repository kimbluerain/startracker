package local.skytrails.app.ui.sky

import local.skytrails.app.ar.CameraVector
import local.skytrails.app.ar.ManualOffset
import local.skytrails.app.core.SkyDirection
import kotlin.math.*

/** True ENU unit axes in display-camera space. Retains roll when following becomes manual. */
data class SkyViewBasis(val east:CameraVector,val north:CameraVector,val up:CameraVector) {
 fun toCamera(direction:SkyDirection):CameraVector {
  val a=Math.toRadians(direction.azimuthDeg);val h=Math.toRadians(direction.altitudeDeg)
  val e=sin(a)*cos(h);val n=cos(a)*cos(h);val u=sin(h)
  return CameraVector(east.xRight*e+north.xRight*n+up.xRight*u,east.yUp*e+north.yUp*n+up.yUp*u,east.zForward*e+north.zForward*n+up.zForward*u)
 }
 fun dragged(yaw:Double,pitch:Double):SkyViewBasis {val offset=ManualOffset(yaw,pitch);return SkyViewBasis(offset.apply(east),offset.apply(north),offset.apply(up))}
 companion object {
  fun from(projection:SkyProjection):SkyViewBasis? {return SkyViewBasis(
   projection.toCamera(SkyDirection(90.0,0.0))?:return null,
   projection.toCamera(SkyDirection(0.0,0.0))?:return null,
   projection.toCamera(SkyDirection(0.0,90.0))?:return null)}
 }
}
