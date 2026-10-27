package local.skytrails.app.ar

import local.skytrails.app.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.math.*

class PoseTimeSeparationTest {
    private val site=PhysicalSite(30.0,120.0,0.0)
    // Upright device: x=east, y=up, screen outward z=south, rear lens faces magnetic north.
    private val north=doubleArrayOf(1.0,0.0,0.0,0.0,0.0,-1.0,0.0,1.0,0.0)
    @Test fun eastDeclinationPlacesTrueNorthLeftAndMagneticNorthCentered() {
        val pose=PoseFrame(site,Instant.parse("2026-10-23T00:00:00Z"),north,10.0)
        val n=pose.toCamera(SkyDirection(0.0,0.0))!!
        assertEquals(-sin(Math.toRadians(10.0)),n.xRight,1e-12)
        assertEquals(cos(Math.toRadians(10.0)),n.zForward,1e-12)
        val magnetic=pose.toCamera(SkyDirection(10.0,0.0))!!
        assertEquals(0.0,magnetic.xRight,1e-12); assertEquals(1.0,magnetic.zForward,1e-12)
    }
    @Test fun previewDatesChangeAstronomyButNeverPhysicalDeclinationInput() {
        val observed=Instant.parse("2026-10-23T00:00:00Z")
        val calls=mutableListOf<Instant>()
        val provider=DeclinationProvider { _, realInstant -> calls.add(realInstant); 10.0 }
        val pose=PoseFrame.capture(site,observed,north,provider)!!
        val astronomy=AstronomyEngineAdapter()
        val s=Site(30.0,120.0,0.0,0.0,ZoneId.of("Asia/Shanghai"))
        val a=astronomy.position(local.skytrails.app.core.Target.MOON,s,Instant.parse("2026-10-23T00:00:00Z"),Atmosphere.AIRLESS)
        val b=astronomy.position(local.skytrails.app.core.Target.MOON,s,Instant.parse("2035-10-23T00:00:00Z"),Atmosphere.AIRLESS)
        assertNotEquals(a,b)
        assertNotEquals(pose.toCamera(a),pose.toCamera(b))
        assertEquals(listOf(observed),calls)
        assertNull(PoseFrame.capture(null,observed,north,provider))
        assertEquals(1,calls.size)
    }
    @Test fun wrapZenithAndSensorAxesDoNotIntroduceSecondSensorRotation() {
        val pose=PoseFrame(site,Instant.EPOCH,north,0.0)
        val left=pose.toCamera(SkyDirection(359.0,0.0))!!
        val right=pose.toCamera(SkyDirection(1.0,0.0))!!
        assertEquals(-left.xRight,right.xRight,1e-12)
        assertEquals(left.zForward,right.zForward,1e-12)
        assertEquals(1.0,pose.toCamera(SkyDirection(173.0,90.0))!!.yUp,1e-12)
        assertNull(pose.toCamera(SkyDirection(Double.NaN,0.0)))
        for(s in listOf(0,90,180,270)) for(d in listOf(0,90,180,270)) {
            val ray=deviceCameraToSensor(CameraVector(0.1,0.2,1.0),s)
            val raw=project(ray,Intrinsics(100.0,100.0,200.0,200.0))!!
            val p=PreviewGeometry.create(1,400,400,400,400,s,d).streamToView.map(raw)
            val expected=when(d) {0->Pixel(210.0,180.0);90->Pixel(180.0,190.0);180->Pixel(190.0,220.0);else->Pixel(220.0,210.0)}
            assertEquals(expected.x,p.x,1e-8); assertEquals(expected.y,p.y,1e-8)
        }
    }
    @Test fun inputRotationMatrixIsCopiedAndInvalidMatricesRejected() {
        val input=north.copyOf(); val pose=PoseFrame(site,Instant.EPOCH,input,0.0)
        input.fill(0.0)
        assertEquals(1.0,pose.toCamera(SkyDirection(0.0,0.0))!!.zForward,1e-12)
        assertNull(PoseFrame.capture(site,Instant.EPOCH,DoubleArray(9),DeclinationProvider {_,_->0.0}))
    }
}
