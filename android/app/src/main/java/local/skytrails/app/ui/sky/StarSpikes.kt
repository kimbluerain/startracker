package local.skytrails.app.ui.sky

import kotlin.math.pow

/** Fixed screen-space optical accent, independent of astronomical position and visual time. */
data class StarSpikeProfile(val coreRadius:Float,val halfLength:Float,val halfWidth:Float,val haloRadius:Float,val strength:Float)
object StarSpikes {
 const val SOFT_WIDTH_SCALE=2.5f
 const val SOFT_ALPHA=.28f
 const val CORE_ALPHA=.80f
 const val HALO_ALPHA=.10f
 fun profile(magnitude:Double,enabled:Boolean):StarSpikeProfile? {
  if(!enabled||magnitude>=2.5||!magnitude.isFinite())return null
  val strength=((2.5-magnitude)/2.5).coerceIn(0.0,1.0).pow(.6).toFloat()
  val radius=(3.4-magnitude*.55).coerceIn(1.15,3.5).toFloat()
  return StarSpikeProfile(radius,radius*(3f+strength),.55f+.2f*strength,radius*2.3f,strength)
 }
 fun intensity(hip:Int,seconds:Float,below:Boolean):Float {
  val pulse=((StarTwinkle.brightness(hip,seconds,true)-.6f)/.4f).coerceIn(0f,1f)
  return (.18f+.82f*pulse)*(if(below).55f/.92f else 1f)
 }
}
