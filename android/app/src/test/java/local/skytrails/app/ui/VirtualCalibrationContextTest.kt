package local.skytrails.app.ui

import local.skytrails.app.ar.*
import local.skytrails.app.ui.renderer.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class VirtualCalibrationContextTest {
 private val now=Instant.parse("2026-10-24T00:00:00Z")
 private val site=PhysicalSite(30.0,120.0,10.0)
 private val offset=ManualOffset(8.0,-3.0)
 private fun image(id:String="fixture",display:Int=0):CameraImage {
  val capture=CaptureGeometry(7,123,id,null,id,LensLock.DIRECT_REAR,0,256,192,
   OpticsMetadata(4.0,8.0,6.0,256,192,RectD(0.0,0.0,256.0,192.0)),RectD(0.0,0.0,256.0,192.0),1f,1,0,0,0,1,RawCalibration(null,null),"fixture")
  return CameraImage(capture,PreviewGeometry.create(1,256,192,256,192,0,display,7))
 }
 private fun runtime():CameraRuntime {val r=CameraRuntime();val image=image();r.rememberCalibrationContext(image);r.calibration.set(image.capture!!.calibrationKey(image.preview),site,now,offset);return r}
 @Test fun virtualCalibrationSurvivesAbsentCameraViewWithinTheSession() {
  val r=runtime();assertNull(r.view);assertEquals(offset,r.skyOffset(0,site,now.plusSeconds(60)))
  assertEquals(offset,r.skyOffset(0,site,now.plusSeconds(86400)))
 }
 @Test fun displayLocationLensAndAgeIndependentlyExpireCachedContext() {
  assertEquals(ManualOffset(),runtime().skyOffset(90,site,now))
  assertEquals(ManualOffset(),runtime().skyOffset(0,PhysicalSite(31.0,120.0,10.0),now))
  assertEquals(ManualOffset(),runtime().skyOffset(0,site,now.plusSeconds(86401)))
  val lens=runtime();lens.rememberCalibrationContext(image("other-lens"));assertEquals(ManualOffset(),lens.skyOffset(0,site,now))
 }
}
