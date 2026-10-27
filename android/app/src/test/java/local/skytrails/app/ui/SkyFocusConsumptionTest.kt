package local.skytrails.app.ui

import local.skytrails.app.core.*
import local.skytrails.app.ui.sky.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SkyFocusConsumptionTest {
 private val site=Site(30.0,120.0,0.0,0.0,ZoneId.of("Asia/Shanghai"))
 private val at=Instant.parse("2026-10-24T10:00:00Z")
 private fun calculation(time:Instant=at,place:Site=site)=SkyCalculation(place,time,SkyDirection(20.0,10.0),SkyDirection(110.0,30.0),GalacticSky(place,time,emptyList(),emptyList(),emptyList(),SkyDirection(220.0,40.0)),emptyList(),emptyList(),emptyList())
 @Test fun nullOldTimeAndWrongSiteDoNotConsumeIntent() {
  val session=SkyFocusConsumption();val r=SkyFocusRequest(1,SkyTarget.SUN,site,at)
  assertNull(session.consume(r,null));assertNull(session.consume(r,calculation(at.minusSeconds(1))));assertNull(session.consume(r,calculation(place=site.copy(latitude=31.0))))
  assertEquals(0L,session.consumed);assertNotNull(session.consume(r,calculation()));assertEquals(1L,session.consumed)
 }
 @Test fun consumedRequestDoesNotResetDraggedViewAfterRecreationButNewSameTargetDoes() {
  val session=SkyFocusConsumption();val r=SkyFocusRequest(7,SkyTarget.SUN,site,at);val c=calculation()
  val focus=session.consume(r,c)!!;val dragged=focus.dragged(19.0,-8.0)
  assertNull(session.consume(r,c));assertNull(session.consume(r.copy(id=6),c))
  assertNotEquals(focus,dragged);assertEquals(focus,session.consume(r.copy(id=8),c))
 }
 @Test fun requestCapturesTargetEvenIfLayerSelectionChangesBeforeCalculation() {
  val selected=local.skytrails.app.location.ManualLocationInput("fixture","30","120","Asia/Shanghai").selection().copy(site=site)
  val s=AppState(location=selected,instant=at).focus(SkyTarget.SUN).toggleLayer(SkyTarget.MOON)
  val basis=SkyFocusConsumption().consume(s.skyFocusRequest,calculation())!!
  val ray=basis.toCamera(SkyDirection(20.0,10.0));assertEquals(0.0,ray.xRight,1e-10);assertEquals(0.0,ray.yUp,1e-10);assertEquals(1.0,ray.zForward,1e-10)
 }
}
