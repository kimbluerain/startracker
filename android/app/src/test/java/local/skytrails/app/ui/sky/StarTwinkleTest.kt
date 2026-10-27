package local.skytrails.app.ui.sky

import org.junit.Assert.*
import org.junit.Test

class StarTwinkleTest {
 @Test fun starsHaveStableIndividualPhasesAndGentleContinuousBrightness() {
  val samples=(0..600).map{StarTwinkle.brightness(24436,it/10f,true)}
  assertTrue("Enabled star must vary in brightness",samples.max()-samples.min()>.15f)
  assertTrue(samples.all{it in .59f..1.001f})
  assertTrue(samples.zipWithNext().all{(a,b)->kotlin.math.abs(a-b)<.08f})
  assertNotEquals(StarTwinkle.brightness(24436,1f,true),StarTwinkle.brightness(27366,1f,true))
  assertEquals(StarTwinkle.brightness(24436,0f,true),StarTwinkle.brightness(24436,60f,true),.0001f)
 }
 @Test fun disabledEffectIsConstantAtEveryTime() {
  for(time in listOf(0f,1f,24f,60f))assertEquals(1f,StarTwinkle.brightness(24436,time,false),0f)
 }
}
