package local.skytrails.app.ui.renderer

/** Preserve source detail, reducing only by powers of two when the GL limit requires it. */
object TextureSampling {
 fun sampleSize(width:Int,height:Int,maxTextureSize:Int):Int {
  require(width>0&&height>0&&maxTextureSize>0)
  var sample=1
  while((maxOf(width,height).toLong()+sample-1)/sample>maxTextureSize)sample*=2
  return sample
 }
}
