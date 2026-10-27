package local.skytrails.app.ar

import org.junit.Assert.*
import org.junit.Test

class ProjectionTest {
    private fun pixel(p: Pixel?, x: Double, y: Double) {
        assertNotNull(p); assertEquals(x, p!!.x, 1e-6); assertEquals(y, p.y, 1e-6)
    }
    @Test fun pinholeRejectsBackfaceAndNonFiniteAndPreservesSigns() {
        val k = Intrinsics(100.0, 100.0, 540.0, 960.0)
        pixel(project(CameraVector(0.0, 0.0, 1.0), k), 540.0, 960.0)
        pixel(project(CameraVector(1.0, 1.0, 1.0), k), 640.0, 860.0)
        for (z in listOf(-1.0, 0.0, Double.NaN)) assertNull(project(CameraVector(0.0, 0.0, z), k))
        assertNull(project(CameraVector(Double.POSITIVE_INFINITY, 0.0, 1.0), k))
        assertNull(project(CameraVector(0.0, 0.0, 1.0), k.copy(fx = 0.0)))
    }
    @Test fun sensorCropAndStreamAspectAreSeparateFromViewportCrop() {
        val c = RectD(0.0, 0.0, 4000.0, 3000.0)
        val e = streamCrop(c, 1920, 1080)!!
        assertEquals(RectD(0.0, 375.0, 4000.0, 2250.0), e)
        pixel(activeToStream(Pixel(2000.0,1500.0), c,1920,1080),960.0,540.0)
        pixel(activeToStream(Pixel(1000.0,1000.0), RectD(500.0,250.0,2000.0,1500.0),1000,750),250.0,375.0)
    }
    @Test fun metadataRequiresRealPositiveDimensionsAndAccountsForActiveOrigin() {
        val m = OpticsMetadata(4.0, 8.0,6.0,4000,3000,RectD(100.0,50.0,3800.0,2900.0))
        val k = m.estimate()!!
        assertEquals(Intrinsics(2000.0,2000.0,1900.0,1450.0), k)
        for (bad in listOf(m.copy(focalMm=null),m.copy(focalMm=0.0),m.copy(physicalWidthMm=Double.NaN),m.copy(pixelHeight=0),m.copy(active=RectD(-1.0,0.0,4000.0,3000.0)))) assertNull(bad.estimate())
    }
    @Test fun fourClockwiseRotationsHaveIndependentCornerExpectations() {
        val expected = listOf(Pixel(100.0,50.0),Pixel(250.0,100.0),Pixel(300.0,250.0),Pixel(50.0,300.0))
        for ((i, degree) in listOf(0,90,180,270).withIndex()) {
            val g = PreviewGeometry.create(1,400,300,if(i%2==0)400 else 300,if(i%2==0)300 else 400,degree,0)
            pixel(g.streamToView.map(Pixel(100.0,50.0)),expected[i].x,expected[i].y)
        }
    }
    @Test fun textureDefaultCancellationAndRoundTripsAllSensorDisplayRotations() {
        for(s in listOf(0,90,180,270)) for(d in listOf(0,90,180,270)) {
            for((w,h) in listOf(400 to 300,1920 to 1080,600 to 600)) {
                val g=PreviewGeometry.create(3,w,h,1080,1920,s,d)
                val default=PreviewGeometry.textureDefault(w,h,1080,1920,s)
                for(p in listOf(Pixel(0.0,0.0),Pixel(w.toDouble(),h.toDouble()),Pixel(w/2.0,h/2.0),Pixel(100.0,50.0))) {
                    val want=g.streamToView.map(p)
                    val actual=g.textureTransform.map(default.map(p))
                    pixel(actual,want.x,want.y)
                    pixel(g.viewToStream.map(want),p.x,p.y)
                }
            }
        }
    }
    @Test fun centerFillPreservesAspectAndClipsEdges() {
        val g=PreviewGeometry.create(1,400,300,300,600,0,0)
        pixel(g.streamToView.map(Pixel(200.0,150.0)),150.0,300.0)
        pixel(g.streamToView.map(Pixel(0.0,0.0)),-250.0,0.0)
    }
    @Test fun frameCacheNeverUsesLatestMetadataForAnotherFrameOrEpoch() {
        val cache=FrameGeometryCache<Int>(2)
        cache.put(1,100,7); cache.put(1,200,8); cache.put(1,300,9)
        assertNull(cache.get(1,100)); assertNull(cache.get(2,200)); assertNull(cache.get(1,250))
        assertEquals(8,cache.get(1,200)); cache.clear(); assertNull(cache.get(1,300))
    }
}
