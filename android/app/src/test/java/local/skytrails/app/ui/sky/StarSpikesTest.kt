package local.skytrails.app.ui.sky

import org.junit.Assert.*
import org.junit.Test

class StarSpikesTest {
 @Test fun brightCatalogStarsHaveLongThinSpikesWhileDimStarsStayRestrained() {
  val bright=StarSpikes.profile(.13,true)
  assertNotNull("Bright stars need an optical spike profile",bright)
  bright!!
  assertTrue(bright.halfLength>=bright.coreRadius*3f)
  assertTrue(bright.halfLength<=bright.coreRadius*4f)
  assertTrue(bright.halfWidth<bright.coreRadius*.3f)
  assertTrue(bright.haloRadius<bright.halfLength)
  val modest=StarSpikes.profile(2.0,true)!!
  assertTrue(bright.halfLength>modest.halfLength)
  assertTrue(bright.strength>modest.strength*2f)
  assertNull(StarSpikes.profile(3.3,true))
 }
 @Test fun mediumBrightStarsHavePerceptibleSoftVariationWithoutOutshiningBrightStars() {
  val medium=StarSpikes.profile(2.2,true)!!
  val alpha=(0..120).map { StarSpikes.intensity(27366,it/10f,false)*medium.strength*StarSpikes.CORE_ALPHA }
  assertTrue("Medium catalog spikes should visibly brighten and dim",alpha.max()-alpha.min()>.15f)
  assertTrue("The thin optical accent remains translucent",alpha.max()<.35f)
  assertTrue(medium.strength<StarSpikes.profile(.13,true)!!.strength*.5f)
 }
 @Test fun disablingRemovesTheWholeAccentAndTimeNeverChangesItsShape() {
  assertNull(StarSpikes.profile(.13,false))
  val profile=StarSpikes.profile(.13,true)
  assertNotNull("Enabled bright star needs a fixed profile",profile)
  val fixed=profile!!
  val intensities=(0..120).map {t->
   assertEquals(fixed,StarSpikes.profile(.13,true))
   StarSpikes.intensity(24436,t/10f,false)
  }
  assertTrue(intensities.max()-intensities.min()>.4f)
  assertTrue(intensities.all{it in .17f..1.001f})
  assertNotEquals(StarSpikes.intensity(24436,1f,false),StarSpikes.intensity(27366,1f,false))
  assertTrue(StarSpikes.intensity(24436,1f,true)<StarSpikes.intensity(24436,1f,false)*.61f)
 }
}
