package local.skytrails.app.ui
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import local.skytrails.app.location.*

class AppStateTest {
 private val now=Instant.parse("2026-10-23T00:00:00Z")
 private fun manual(onsite:Boolean=false)=ManualLocationInput("测试","30","120","Asia/Shanghai",confirmedOnSite=onsite).selection()
 @Test fun noPermissionStillAllowsManualPlanningButNeverCamera() {
  val s=AppState(instant=now).select(manual()).copy(tab=AppTab.PLAN)
  assertNotNull(s.location);assertFalse(s.cameraEligible(now));assertFalse(s.physicalEligible(now))
 }
 @Test fun explicitManualOnsiteNeedsCameraPermissionButNotGpsPermission() {
  val s=AppState(instant=now,cameraPermission=true).select(manual(true))
  assertTrue(s.cameraEligible(now));assertFalse(s.copy(tab=AppTab.PLAN).cameraEligible(now))
 }
 @Test fun deviceFixExpiresAndPermissionRevocationDisablesIt() {
  val r=LocationRepository();r.acceptDeviceFix(DeviceFix(30.0,120.0,now,15.0,"gps"),ZoneId.of("Asia/Shanghai"))
  val s=AppState(location=r.state.value.selected,instant=now,cameraPermission=true,locationPermission=true)
  assertTrue(s.cameraEligible(now));assertFalse(s.cameraEligible(now.plusSeconds(901)))
  assertFalse(s.copy(locationPermission=false).cameraEligible(now))
 }
 @Test fun previewSurvivesTickerAndPlaceChangeUntilNow() {
  val future=now.plusSeconds(3600);val s=AppState(instant=now).preview(future).select(manual())
  assertFalse(s.live);assertEquals(future,s.tick(now.plusSeconds(1)).instant)
  assertTrue(s.now(now).live);assertEquals(now,s.now(now).instant)
 }
 @Test fun sliderUsesActualDstDayAndNeverNextDate() {
  val z=ZoneId.of("America/New_York")
  assertEquals(Instant.parse("2026-03-08T16:30:00Z"),instantOnDay(LocalDate.parse("2026-03-08"),z,0.5))
  assertEquals(Instant.parse("2026-11-01T16:30:00Z"),instantOnDay(LocalDate.parse("2026-11-01"),z,0.5))
  assertEquals(LocalDate.parse("2026-11-01"),instantOnDay(LocalDate.parse("2026-11-01"),z,1.0).atZone(z).toLocalDate())
 }
 @Test fun moonEventSelectsMoonAndItsExactInstantAtomically() {
  val selected=AppState(instant=now,target=SkyTarget.SUN).event(SkyTarget.MOON,now.plusSeconds(31))
  assertEquals(SkyTarget.MOON,selected.target);assertEquals(now.plusSeconds(31),selected.instant);assertFalse(selected.live)
 }
 @Test fun delayedCalculationCannotOverwriteLatestRequest() {
  val r=LatestRequest();val first=r.begin();val second=r.begin();assertFalse(r.accepts(first));assertTrue(r.accepts(second))
 }
}
