package local.skytrails.app.ui

import local.skytrails.app.ar.*
import local.skytrails.app.core.SkyDirection
import local.skytrails.app.location.*
import local.skytrails.app.ui.sky.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import kotlin.math.*

class VirtualFollowTest {
 private val now=Instant.parse("2026-10-24T00:00:00Z")
 private val site=PhysicalSite(30.0,120.0,0.0)
 private val north=doubleArrayOf(1.0,0.0,0.0,0.0,0.0,-1.0,0.0,1.0,0.0)
 private fun manual(onsite:Boolean)=ManualLocationInput("fixture","30","120","Asia/Shanghai",confirmedOnSite=onsite).selection()
 @Test fun independentOffCenterRayLandsInAllFourDisplayQuadrants() {
  // True ENU ray (.2, 1, .3), facing north. f=200, center=(200,150).
  val direction=SkyDirection(Math.toDegrees(atan(.2)),Math.toDegrees(atan(.3/sqrt(1.04))))
  val pose=PoseFrame(site,now,north,0.0)
  val expected=listOf(Pixel(240.0,90.0),Pixel(140.0,110.0),Pixel(160.0,210.0),Pixel(260.0,190.0))
  for((display,point)in listOf(0,90,180,270).zip(expected)) {
   val p=SkyProjection.sensor(400,300,90.0,pose,display)
   val actual=(GalacticOverlay.target(direction,p) as TargetIndicator.Visible).pixel
   assertEquals("display $display x",point.x,actual.x,1e-8);assertEquals("display $display y",point.y,actual.y,1e-8)
  }
 }
 @Test fun rolledSensorHasIndependentNonCentralLandmarkAndManualTakeoverKeepsRoll() {
  // Device rolled 30 degrees: right=(cos30,0,sin30), up=(-sin30,0,cos30), back=(0,-1,0).
  val c=sqrt(3.0)/2;val matrix=doubleArrayOf(c,-.5,0.0,0.0,0.0,-1.0,.5,c,0.0)
  val pose=PoseFrame(site,now,matrix,0.0)
  val target=SkyDirection(Math.toDegrees(atan(.2)),Math.toDegrees(atan(.3/sqrt(1.04))))
  val expectedX=200+200*(.2*c+.3*.5);val expectedY=150-200*(-.2*.5+.3*c)
  val p=SkyProjection.sensor(400,300,90.0,pose,0)
  val frozen=SkyProjection.manual(400,300,90.0,SkyViewBasis.from(p)!!)
  for(projection in listOf(p,frozen)) {
   val pixel=(GalacticOverlay.target(target,projection) as TargetIndicator.Visible).pixel
   assertEquals(expectedX,pixel.x,1e-8);assertEquals(expectedY,pixel.y,1e-8)
  }
 }
 @Test fun eastAndWestDeclinationsHaveOppositeHorizontalOffsets() {
  for(d in listOf(-12.0,12.0)) {
   val p=SkyProjection.sensor(400,300,90.0,PoseFrame(site,now,north,d),0)
   val n=(GalacticOverlay.target(SkyDirection(0.0,0.0),p) as TargetIndicator.Visible).pixel
   assertEquals(200-200*tan(Math.toRadians(d)),n.x,1e-8);assertEquals(150.0,n.y,1e-8)
  }
 }
 @Test fun calibrationIsAppliedInDisplaySpaceAfterRotation() {
  val p=SkyProjection.sensor(400,300,90.0,PoseFrame(site,now,north,0.0),90,ManualOffset(10.0,0.0))
  val n=(GalacticOverlay.target(SkyDirection(0.0,0.0),p) as TargetIndicator.Visible).pixel
  assertEquals(200+200*tan(Math.toRadians(10.0)),n.x,1e-8);assertEquals(150.0,n.y,1e-8)
 }
 @Test fun remoteCityAndPreviewYearKeepPhysicalSessionContextButNeverQualifyRemoteAr() {
  val physical=manual(true);val remote=manual(false).copy(source=LocationSource.CITY_CENTER)
  val state=AppState(realNow=now).select(physical).select(remote).preview(Instant.parse("2045-01-01T00:00:00Z"))
  assertEquals(physical,state.poseLocation(now));assertEquals(remote,state.location);assertFalse(state.physicalEligible(now))
  assertEquals(now,state.realNow);assertNull(AppState(location=remote,realNow=now).poseLocation(now))
 }
 @Test fun cachedDeviceFixStillExpiresAndPermissionRevocationCannotResurrectIt() {
  val fix=manual(false).copy(source=LocationSource.DEVICE,fixAt=now)
  val remote=manual(false).copy(source=LocationSource.CITY_CENTER)
  val state=AppState(realNow=now,locationPermission=true).select(fix).select(remote)
  assertEquals(fix,state.poseLocation(now.plusSeconds(900)));assertNull(state.poseLocation(now.plusSeconds(901)))
  assertNull(state.copy(locationPermission=false).poseLocation(now));assertNull(state.poseLocation(now.minusSeconds(1)))
 }
 @Test fun unconfirmedManualNeverSuppliesPhysicalContextAndNextConfirmedSiteReplacesCache() {
  val first=manual(true);val second=ManualLocationInput("other","40","116","Asia/Shanghai",confirmedOnSite=true).selection()
  assertNull(AppState(realNow=now).select(manual(false)).poseLocation(now))
  assertEquals(second,AppState(realNow=now).select(first).select(second).poseLocation(now))
 }
 @Test fun layerTogglesPreserveFollowButExplicitFocusEntersManualEvenForSameTarget() {
  val state=AppState(target=SkyTarget.GALAXY)
  assertTrue(state.skyFollow);assertTrue(state.toggleLayer(SkyTarget.SUN).skyFollow)
  assertEquals(0L,state.toggleLayer(SkyTarget.SUN).focusRequest)
  val focus=state.focus(SkyTarget.GALAXY);assertFalse(focus.skyFollow);assertEquals(1L,focus.focusRequest)
  assertFalse(state.event(SkyTarget.MOON,now).skyFollow)
 }
}
