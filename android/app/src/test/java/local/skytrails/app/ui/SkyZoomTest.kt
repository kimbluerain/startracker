package local.skytrails.app.ui

import local.skytrails.app.ui.sky.SkyZoom
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.tan

class SkyZoomTest {
 @Test fun twoTimesPinchDoublesFocalLengthRatherThanDividingAngle() {
  val start=70.0;val next=SkyZoom.scaled(start,2.0)
  assertEquals(2.0,tan(Math.toRadians(start/2))/tan(Math.toRadians(next/2)),1e-8)
  assertEquals(start,SkyZoom.scaled(next,.5),1e-8)
 }
 @Test fun zoomIsBoundedAndBadGestureCannotCorruptProjection() {
  assertEquals(20.0,SkyZoom.scaled(70.0,100.0),0.0)
  assertEquals(100.0,SkyZoom.scaled(70.0,.01),0.0)
  for(value in listOf(0.0,-1.0,Double.NaN,Double.POSITIVE_INFINITY))assertEquals(70.0,SkyZoom.scaled(70.0,value),0.0)
 }
}
