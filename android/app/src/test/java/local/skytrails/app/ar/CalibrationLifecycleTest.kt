package local.skytrails.app.ar
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class CalibrationLifecycleTest {
    private val site=PhysicalSite(30.0,120.0,0.0)
    private val key=CalibrationKey("back","wide",90,0,1920,1080,"crop/focal/distortion")
    @Test fun calibrationExpiresOnLocationLensDisplayOrOpticalChange() {
        val c=CalibrationStore(); val now=Instant.parse("2026-10-23T00:00:00Z")
        c.set(key,site,now,ManualOffset(2.0,-3.0))
        assertEquals(CalibrationStatus.APPLIED,c.resolve(key,site,now).status)
        assertEquals(CalibrationStatus.EXPIRED,c.resolve(key.copy(physicalId="tele"),site,now).status)
        assertEquals(ManualOffset(),c.resolve(key,site,now).offset) // Expiration is sticky until reconfirmation.
        c.set(key,site,now,ManualOffset(2.0,-3.0))
        assertEquals(CalibrationStatus.EXPIRED,c.resolve(key,site.copy(latitude=31.0),now).status)
        c.set(key,site,now,ManualOffset())
        assertEquals(CalibrationStatus.EXPIRED,c.resolve(key.copy(displayDegrees=90),site,now).status)
        c.set(key,site,now,ManualOffset())
        assertEquals(CalibrationStatus.EXPIRED,c.resolve(key,site,now.plusSeconds(86401)).status)
    }
    @Test fun manualYawAndPitchAreIndependentAxesAndRemainContinuous() {
        val forward=CameraVector(0.0,0.0,1.0)
        val yaw=ManualOffset(90.0,0.0).apply(forward)
        assertEquals(1.0,yaw.xRight,1e-12); assertEquals(0.0,yaw.yUp,1e-12)
        val pitch=ManualOffset(0.0,90.0).apply(forward)
        assertEquals(0.0,pitch.xRight,1e-12); assertEquals(1.0,pitch.yUp,1e-12)
        assertEquals(ManualOffset(-1.0,0.0).apply(forward),ManualOffset(359.0,0.0).apply(forward))
    }
    @Test fun manualOffsetsFollowDisplayedAxesInEveryOrientation() {
        val k=Intrinsics(100.0,100.0,200.0,200.0)
        for(d in listOf(0,90,180,270)) {
            val shifted=ManualOffset(10.0,0.0).applyForDisplay(CameraVector(0.0,0.0,1.0),d)
            val sensor=deviceCameraToSensor(shifted,90)
            val p=PreviewGeometry.create(1,400,400,400,400,90,d).streamToView.map(project(sensor,k)!!)
            assertEquals(217.6326980708465,p.x,1e-8)
            assertEquals(200.0,p.y,1e-8)
        }
    }
    @Test fun delayedOpenAndSessionCannotResurrectStoppedOrNewEpoch() {
        val lifecycle=CameraLifecycle()
        val first=lifecycle.start(true,true,true)!!
        assertNull(lifecycle.start(true,true,true))
        lifecycle.stop(); assertFalse(lifecycle.opened(first)); assertFalse(lifecycle.configured(first))
        val next=lifecycle.start(true,true,true)!!
        assertFalse(lifecycle.opened(first)); assertTrue(lifecycle.opened(next)); assertFalse(lifecycle.configured(first))
        assertTrue(lifecycle.configured(next)); assertTrue(lifecycle.accepts(next))
        lifecycle.fail(first); assertTrue(lifecycle.accepts(next))
        lifecycle.fail(next); assertFalse(lifecycle.accepts(next))
        assertNull(lifecycle.start(false,true,true)); assertNull(lifecycle.start(true,false,true)); assertNull(lifecycle.start(true,true,false))
    }
    @Test fun repeatedStopAndPermissionRevocationInvalidateGeometry() {
        val gate=CameraLifecycle(); val e=gate.start(true,true,true)!!
        gate.opened(e); gate.configured(e); gate.stop(); gate.stop()
        assertFalse(gate.accepts(e)); assertNull(gate.start(true,false,true))
    }
}
