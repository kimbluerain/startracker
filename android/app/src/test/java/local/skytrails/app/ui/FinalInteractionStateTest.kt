package local.skytrails.app.ui

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import local.skytrails.app.core.SkyDirection
import local.skytrails.app.location.*
import local.skytrails.app.ui.sky.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class FinalInteractionStateTest {
 private val now=Instant.parse("2026-10-24T11:30:00Z")
 private val place=ManualLocationInput("test","30","120","Asia/Shanghai",confirmedOnSite=true).selection()
 private fun request()=SkyRenderRequest(SkyDirectionRequest(place.site,now.epochSecond/60),220f,20f,70.0,480,1000,true,false,.7f,Any())
 @Test fun allPhotographicInputsInvalidateOldResultIncludingDirectionAndSource() {
  val a=request();val result=RequestedSkyResult(a,"old pixels")
  val changes=listOf(a.copy(direction=a.direction.copy(site=place.site.copy(latitude=31.0))),a.copy(direction=a.direction.copy(minute=a.direction.minute!!+1)),
   a.copy(azimuth=221f),a.copy(altitude=21f),a.copy(horizontalFov=65.0),a.copy(width=481),a.copy(height=1001),
   a.copy(galaxy=false),a.copy(night=true),a.copy(exposure=.5f),a.copy(sourceIdentity=Any()))
  assertEquals("old pixels",result.forRequest(a))
  changes.forEach{assertNull(result.forRequest(it))}
  val directions=RequestedSkyResult(a.direction,listOf(SkyDirection(1.0,2.0)))
  assertNull(directions.forRequest(a.direction.copy(minute=a.direction.minute!!+1)))
  assertNull(directions.forRequest(a.direction.copy(site=place.site.copy(longitude=121.0))))
 }
 @Test fun lateAsyncAResultCannotAppearWhileBThemeOrLayerIsExpected()=runTest {
  val a=request();val b=a.copy(azimuth=45f,night=true,galaxy=false)
  val aDone=CompletableDeferred<String>();val bDone=CompletableDeferred<String>()
  var result:RequestedSkyResult<SkyRenderRequest,String>?=null
  val jobs=listOf(launch{result=RequestedSkyResult(a,aDone.await())},launch{result=RequestedSkyResult(b,bDone.await())})
  bDone.complete("B pixels");runCurrent();assertEquals("B pixels",result?.forRequest(b))
  aDone.complete("late A pixels");runCurrent();assertNull(result?.forRequest(b))
  jobs.forEach{it.join()}
 }
 @Test fun explicitFocusAlwaysRecentersWithoutChangingLayerChoices() {
  for(bits in 0..7)for(before in SkyTarget.entries)for(selected in SkyTarget.entries) {
   val s=AppState(target=before,layers=SkyLayers(bits and 1!=0,bits and 2!=0,bits and 4!=0),focusRequest=14)
   val next=s.focus(selected)
   assertEquals(selected,next.target);assertEquals(15L,next.focusRequest);assertEquals(s.layers,next.layers)
  }
 }
 @Test fun cameraIntentSurvivesPermissionSuccessButNeverBypassesDenialOrFreshness() {
  val s=AppState(location=place,virtualSky=true,cameraPermission=false,realNow=now)
  val pending=s.requestCameraMode(now)
  assertFalse(pending.virtualSky);assertFalse(pending.cameraEligible(now))
  assertTrue(pending.copy(cameraPermission=true).cameraEligible(now))
  val repo=LocationRepository();repo.acceptDeviceFix(DeviceFix(30.0,120.0,now,10.0,"fixture"),ZoneId.of("Asia/Shanghai"))
  val device=s.copy(location=repo.state.value.selected,locationPermission=true)
  assertFalse(device.requestCameraMode(now).copy(cameraPermission=true).cameraEligible(now.plusSeconds(901)))
  assertTrue(device.requestCameraMode(now.plusSeconds(901)).virtualSky)
  assertTrue(s.copy(location=place.copy(confirmedManualOnSite=false)).requestCameraMode(now).virtualSky)
 }
 @Test fun guidanceUsesCurrentProjectionAndOnlyFocusedVisibleBehindTarget() {
  val projection=SkyProjection.virtual(800,1200,70.0,0.0,0.0)
  val layers=SkyLayers()
  assertNull(focusedTargetGuidance(SkyTarget.GALAXY,layers,SkyDirection(0.0,0.0),projection,true))
  assertNull(focusedTargetGuidance(SkyTarget.GALAXY,layers,SkyDirection(65.0,0.0),projection,true))
  assertNull(focusedTargetGuidance(SkyTarget.GALAXY,layers,SkyDirection(180.0,0.0),projection,true))
  assertNull(focusedTargetGuidance(SkyTarget.GALAXY,layers,SkyDirection(180.0,-10.0),projection,false))
  assertEquals("月亮在身后 · 拖动寻找 · 地平线下",focusedTargetGuidance(SkyTarget.MOON,layers,SkyDirection(180.0,-10.0),projection,false))
  assertNull(focusedTargetGuidance(SkyTarget.GALAXY,layers.copy(galaxy=false),SkyDirection(180.0,0.0),projection,true))
  assertNull(focusedTargetGuidance(SkyTarget.SUN,layers,SkyDirection(180.0,0.0),null,true))
 }
}
