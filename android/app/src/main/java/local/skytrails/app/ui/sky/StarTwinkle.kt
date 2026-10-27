package local.skytrails.app.ui.sky

import kotlin.math.PI
import kotlin.math.sin

/** Brightness only: astronomical coordinates never depend on this visual clock. */
object StarTwinkle {
 const val FRAME_MILLIS=50L
 fun seconds():Float=((System.nanoTime()/1_000_000L)%60_000L)/1000f
 fun brightness(hip:Int,seconds:Float,enabled:Boolean):Float {
  if(!enabled)return 1f
  val phase=(hip%997)*2*PI/997
  val frequency=2*PI*(9+hip%13)/60
  return (.80+.16*sin(phase+seconds*frequency)+.04*sin(phase*.7+seconds*frequency*2)).toFloat()
 }
}
