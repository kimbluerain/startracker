package local.skytrails.app.ui.sky

import kotlin.math.atan
import kotlin.math.tan

/** Zoom changes focal length, shared by the sky photograph and all projected objects. */
object SkyZoom {
 const val DEFAULT_FOV=70.0
 const val MIN_FOV=20.0
 const val MAX_FOV=100.0
 fun scaled(fov:Double,gestureScale:Double):Double {
  if(!gestureScale.isFinite()||gestureScale<=0.0)return fov
  return Math.toDegrees(2*atan(tan(Math.toRadians(fov/2))/gestureScale)).coerceIn(MIN_FOV,MAX_FOV)
 }
}
