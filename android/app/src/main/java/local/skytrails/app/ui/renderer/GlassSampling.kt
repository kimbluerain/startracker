package local.skytrails.app.ui.renderer
import local.skytrails.app.ar.*
/** Coordinates are camera viewport pixels, never window/root pixels. */
data class GlassMask(val left:Float,val top:Float,val right:Float,val bottom:Float,val radius:Float)
object GlassSampling {
 /** Without a known mount we keep the producer transform, which expects bottom-left GL UVs.
  * The quad starts at the top left: return actual * flipY, without inferring camera geometry. */
 fun unmountedViewToSampler(actual:FloatArray):FloatArray {
  require(actual.size==16)
  return actual.copyOf().apply {
   for(row in 0..3) {
    this[4+row]=-actual[4+row]
    this[12+row]=actual[12+row]+actual[4+row]
   }
  }
 }
 fun viewToSampler(preview:PreviewGeometry,actual:Mat3):Mat3 {
  // Cancelling predicted() leaves native OES image coordinates (top-left, y-down).
  // viewToStream already uses that origin. Ordinary 2D/FBO flips are separate.
  val normalized=Mat3.scaleTranslate(1.0/preview.streamWidth,1.0/preview.streamHeight)*preview.viewToStream
  return actual*predicted(preview.sensorDegrees).inverse()*normalized
 }
 fun residualAllowsOverlay(preview:PreviewGeometry,actual:Mat3):Boolean {
  val residual=actual*predicted(preview.sensorDegrees).inverse()
  return listOf(Pixel(0.0,0.0),Pixel(1.0,0.0),Pixel(0.0,1.0),Pixel(1.0,1.0)).all { p->
   val q=residual.map(p)
   kotlin.math.abs(q.x-p.x)*preview.streamWidth<=2.01 && kotlin.math.abs(q.y-p.y)*preview.streamHeight<=2.01
  }
 }
 fun predicted(sensor:Int):Mat3=Mat3.scaleTranslate(1.0,-1.0,0.0,1.0)*Mat3.rotation(sensor,1,1)
 fun localMask(window:GlassMask,viewX:Float,viewY:Float)=window.copy(left=window.left-viewX,top=window.top-viewY,right=window.right-viewX,bottom=window.bottom-viewY)
}
