package local.skytrails.app.ar
import org.junit.Assert.*
import org.junit.Test

class CaptureGeometryTest {
    private fun frame()=CaptureGeometry(7,123,"back","wide","wide",LensLock.PHYSICAL_OUTPUT,90,1920,1080,
        OpticsMetadata(4.0,8.0,6.0,4000,3000,RectD(0.0,0.0,4000.0,3000.0)),
        RectD(0.0,0.0,4000.0,3000.0),1f,1,0,0,0,1,RawCalibration(null,null),"capture")
    @Test fun mixedLogicalAndPhysicalMetadataNeverProjects() {
        val good=frame();val view=PreviewGeometry.create(1,1920,1080,1080,1920,90,0,7)
        assertEquals(OpticsQuality.METADATA_ESTIMATE,good.quality)
        assertEquals(Pixel(540.0,960.0),good.projectNatural(CameraVector(0.0,0.0,1.0),view))
        assertNull(good.copy(metadataCameraId="back").projectNatural(CameraVector(0.0,0.0,1.0),view))
        assertEquals(OpticsQuality.UNAVAILABLE,good.copy(lock=LensLock.UNLOCKED_LOGICAL).quality)
    }
    @Test fun missingCropUnknownGeometryAndStabilizationDisableOverlay() {
        val f=frame()
        for(bad in listOf(f.copy(crop=null),f.copy(crop=RectD(-1.0,0.0,4000.0,3000.0)),f.copy(crop=RectD(0.0,0.0,4001.0,3000.0)),f.copy(sensorDegrees=null),f.copy(zoomRatio=2f),f.copy(rotateAndCrop=1),f.copy(videoStabilization=1),f.copy(opticalStabilization=1),f.copy(distortionMode=null),f.copy(optics=f.optics.copy(focalMm=null)))) assertEquals(OpticsQuality.UNAVAILABLE,bad.quality)
    }
    @Test fun captureUsesRealCropAndRejectsAnotherStreamConfiguration() {
        val f=frame().copy(sensorDegrees=0,crop=RectD(1000.0,750.0,2000.0,1500.0))
        val p=f.projectNatural(CameraVector(0.1,0.0,1.0),PreviewGeometry.create(3,1920,1080,1920,1080,0,0,7))!!
        assertEquals(1152.0,p.x,1e-8);assertEquals(540.0,p.y,1e-8)
        assertNull(f.projectNatural(CameraVector(0.0,0.0,1.0),PreviewGeometry.create(4,1280,720,1920,1080,0,0,7)))
    }
    @Test fun frameFromAnotherEpochOrSensorMountCannotReuseViewport() {
        val f=frame()
        val stale=PreviewGeometry.create(1,1920,1080,1080,1920,90,0,6)
        val differentMount=PreviewGeometry.create(2,1920,1080,1080,1920,270,0,7)
        assertNull(f.projectNatural(CameraVector(0.0,0.0,1.0),stale))
        assertNull(f.projectNatural(CameraVector(0.0,0.0,1.0),differentMount))
    }
    @Test fun optionalCalibrationArraysCannotBeMutatedAcrossThreads() {
        val original=floatArrayOf(100f,100f,50f,50f,0f);val raw=RawCalibration(original,null)
        original[0]=999f;val copy=raw.intrinsics()!!;copy[0]=888f
        assertEquals(100f,raw.intrinsics()!![0],0f)
    }
}
