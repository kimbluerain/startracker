package local.skytrails.app.ui.renderer

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class FieldStarsTest {
 private fun binary():ByteArray=ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN).apply {
  put("STF1".toByteArray());putInt(1);putInt(24436)
  listOf(78.634467f,-8.201638f,1.31f,.50f,.13f,-.03f).forEach{putFloat(it)}
 }.array()
 @Test fun readsExactLittleEndianCatalogAndRejectsTruncatedRecords() {
  val stars=FieldStars.read(ByteArrayInputStream(binary()))
  assertEquals(1,stars.size);assertEquals(24436,stars.single().hip)
  assertEquals(78.634467f,stars.single().raDeg,0f);assertEquals(-.03f,stars.single().bv,0f)
  assertThrows(IllegalArgumentException::class.java){FieldStars.read(ByteArrayInputStream(binary().copyOf(35)))}
  assertThrows(IllegalArgumentException::class.java){FieldStars.read(ByteArrayInputStream(binary().apply{this[0]=0}))}
 }
 @Test fun galacticOriginAndTangentMotionPreserveTheEquatorialCosDecConvention() {
  // ICRS direction of the Galactic origin, independent published rotation anchor.
  val origin=FieldStars.galacticMotion(FieldStar(1,266.404995f,-28.936174f,0f,0f,1f,.65f))
  assertEquals(1.0,origin[0].toDouble(),1e-6);assertEquals(0.0,origin[1].toDouble(),1e-6);assertEquals(0.0,origin[2].toDouble(),1e-6)
  val moving=FieldStars.galacticMotion(FieldStar(2,0f,60f,3600000f,0f,2f,.65f))
  val speed=sqrt((3..5).sumOf{moving[it].toDouble().pow(2)})
  assertEquals("RA proper motion already includes cos(dec)",PI/180,speed,1e-8)
  assertEquals("Motion is tangent to the unit sphere",0.0,(0..2).sumOf{moving[it].toDouble()*moving[it+3]},1e-8)
 }
 @Test fun zoomRevealsFainterCatalogStarsSmoothlyWithoutExceedingAvailableDepth() {
  assertTrue(FieldStars.magnitudeLimit(20.0)>FieldStars.magnitudeLimit(70.0)+2f)
  assertTrue(FieldStars.magnitudeLimit(70.0)>FieldStars.magnitudeLimit(100.0))
  assertTrue(FieldStars.magnitudeLimit(20.0)<=9.5f)
  assertTrue(abs(FieldStars.magnitudeLimit(40.0)-FieldStars.magnitudeLimit(40.01))<.01f)
 }
}
