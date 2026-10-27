package local.skytrails.app.ui.sky

import java.time.Instant
import local.skytrails.app.ar.*
import local.skytrails.app.core.*
import org.junit.Assert.*
import org.junit.Test

class GalacticOverlayTest {
    private val projection=SkyProjection.virtual(400,300,90.0,0.0,0.0)
    private fun d(az:Double,alt:Double=0.0)=SkyDirection(az,alt)
    private fun assertPixel(p:Pixel,x:Double,y:Double) { assertEquals(x,p.x,1e-6);assertEquals(y,p.y,1e-6) }

    // Prevents reintroducing a second ENU sign/axis convention for the virtual camera.
    @Test fun virtualCameraReusesSharedAxesAndPinholeProjection() {
        val center=GalacticOverlay.target(d(0.0),projection) as? TargetIndicator.Visible
        assertNotNull(center);assertPixel(center!!.pixel,200.0,150.0)
        val right=GalacticOverlay.target(d(30.0),projection) as TargetIndicator.Visible
        assertPixel(right.pixel,315.47005383792515,150.0)
        val above=GalacticOverlay.target(d(0.0,30.0),projection) as TargetIndicator.Visible
        assertPixel(above.pixel,200.0,34.52994616207485)
        val aimed=SkyProjection.virtual(400,300,90.0,232.0,23.0)
        assertPixel((GalacticOverlay.target(d(232.0,23.0),aimed) as TargetIndicator.Visible).pixel,200.0,150.0)
    }

    @Test fun crossingViewportWithBothEndsOutsideClipsToEdges() {
        val segments=GalacticOverlay.path(listOf(d(300.0),d(60.0)),projection)
        assertEquals(1,segments.size)
        assertPixel(segments[0].start,0.0,150.0); assertPixel(segments[0].end,400.0,150.0)
    }

    @Test fun backfacesDoNotJoinAcrossScreenButFrontToBackReachesEdge() {
        assertTrue(GalacticOverlay.path(listOf(d(120.0),d(240.0)),projection).isEmpty())
        val segments=GalacticOverlay.path(listOf(d(0.0),d(120.0)),projection)
        assertEquals(1,segments.size)
        assertPixel(segments[0].start,200.0,150.0);assertPixel(segments[0].end,400.0,150.0)
        assertTrue(GalacticOverlay.path(listOf(d(0.0),d(180.0)),projection).isEmpty())
    }

    @Test fun longitudeWrapStaysShortAndHorizonCrossingSplitsLineStyle() {
        val wrap=GalacticOverlay.path(listOf(d(359.0),d(1.0)),projection)
        assertEquals(1,wrap.size); assertTrue(wrap.single().end.x-wrap.single().start.x<8)
        val split=GalacticOverlay.path(listOf(d(0.0,10.0),d(0.0,-10.0)),projection)
        assertEquals(2,split.size)
        assertFalse(split[0].belowHorizon); assertTrue(split[1].belowHorizon)
        assertPixel(split[0].end,200.0,150.0); assertEquals(split[0].end,split[1].start)
    }

    @Test fun frontOutsideHasRealEdgeDirectionButBehindHasNoFakeFrontArrow() {
        val edge=GalacticOverlay.target(d(60.0,-5.0),projection) as? TargetIndicator.Edge
        assertNotNull(edge); assertEquals(388.0,edge!!.pixel.x,1e-6)
        assertTrue(edge.belowHorizon); assertTrue(edge.angleRadians>0)
        assertTrue(GalacticOverlay.target(d(180.0),projection) is TargetIndicator.Behind)
        assertTrue(GalacticOverlay.target(d(0.0,Double.NaN),projection) is TargetIndicator.Unavailable)
    }

    @Test fun invalidDirectionsBreakPathsInsteadOfConnectingAcrossGap() {
        assertTrue(GalacticOverlay.path(listOf(d(359.0),d(Double.NaN),d(1.0)),projection).isEmpty())
        assertTrue(GalacticOverlay.path(emptyList(),projection).isEmpty())
    }

    @Test fun realCameraUsesCaptureMountCropViewportAndCalibrationWithoutFakeMetadata() {
        val pose=PoseFrame(PhysicalSite(0.0,0.0,0.0),Instant.EPOCH,doubleArrayOf(1.0,0.0,0.0,0.0,0.0,-1.0,0.0,1.0,0.0),0.0)
        val optics=OpticsMetadata(4.0,8.0,6.0,400,300,RectD(0.0,0.0,400.0,300.0))
        val capture=CaptureGeometry(1,10,"rear",null,"rear",LensLock.DIRECT_REAR,90,400,300,optics,RectD(0.0,0.0,400.0,300.0),1f,0,0,0,0,1,RawCalibration(null,null),"test")
        val preview=PreviewGeometry.create(1,400,300,300,400,90,0,1)
        val real=SkyProjection.camera(pose,capture,preview,ManualOffset(10.0,0.0))
        assertNotNull(real)
        val actual=GalacticOverlay.target(d(0.0),real!!) as TargetIndicator.Visible
        // Sensor mounting and preview rotation cancel; positive calibrated yaw moves right.
        assertPixel(actual.pixel,185.265396141693,200.0)
        assertNull(SkyProjection.camera(pose,capture.copy(distortionMode=null),preview))
        assertNull(SkyProjection.camera(pose,capture,preview.copy(epoch=2)))
    }
}
