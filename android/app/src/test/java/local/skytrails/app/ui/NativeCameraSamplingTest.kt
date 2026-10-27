package local.skytrails.app.ui

import local.skytrails.app.ar.*
import local.skytrails.app.ui.renderer.GlassSampling
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Hand-checked image-space fixtures. Expected points never use the transform under test. */
@RunWith(Parameterized::class)
class NativeCameraSamplingTest(private val sensor: Int, private val display: Int) {
    private val turn = (sensor - display + 360) % 360 / 90
    // AOSP rear-camera SurfaceTexture matrices, written independently of predicted().
    private val actual = listOf(
        Mat3.of(1.0, 0.0, 0.0, 0.0, -1.0, 1.0),
        Mat3.of(0.0, -1.0, 1.0, -1.0, 0.0, 1.0),
        Mat3.of(-1.0, 0.0, 1.0, 0.0, 1.0, 0.0),
        Mat3.of(0.0, 1.0, 0.0, 1.0, 0.0, 0.0)
    )[sensor / 90]

    private fun assertPixel(message: String, expected: Pixel, actual: Pixel?) {
        assertNotNull(message, actual)
        assertEquals("$message x", expected.x, actual!!.x, 1e-9)
        assertEquals("$message y", expected.y, actual.y, 1e-9)
    }

    @Test fun asymmetricNativeCornersKeepTheirOrderForEveryMountAndDisplay() {
        val width = if (turn % 2 == 0) 400 else 200
        val height = if (turn % 2 == 0) 200 else 400
        val preview = PreviewGeometry.create(1, 400, 200, width, height, sensor, display)
        val sampling = GlassSampling.viewToSampler(preview, actual)
        // TL, TR, BL, BR samples of a top-row AB / bottom-row CD image.
        val expected = listOf(
            listOf(Pixel(.1, .1), Pixel(.9, .1), Pixel(.1, .9), Pixel(.9, .9)),
            listOf(Pixel(.1, .9), Pixel(.1, .1), Pixel(.9, .9), Pixel(.9, .1)),
            listOf(Pixel(.9, .9), Pixel(.1, .9), Pixel(.9, .1), Pixel(.1, .1)),
            listOf(Pixel(.9, .1), Pixel(.9, .9), Pixel(.1, .1), Pixel(.1, .9))
        )[turn]
        val screen = listOf(Pixel(width * .1, height * .1), Pixel(width * .9, height * .1),
            Pixel(width * .1, height * .9), Pixel(width * .9, height * .9))
        screen.zip(expected).forEachIndexed { i, (point, want) ->
            assertPixel("S=$sensor D=$display corner=$i", want, sampling.map(point))
        }
        assertTrue(GlassSampling.residualAllowsOverlay(preview, actual))
    }

    @Test fun centerFillCropsRectangularNativeBufferWithoutChangingHandedness() {
        val preview = PreviewGeometry.create(1, 400, 200, 200, 200, sensor, display)
        val sampling = GlassSampling.viewToSampler(preview, actual)
        // The 200x200 viewport keeps sensor x=100..300. These samples are 20px inside it.
        val expected = listOf(
            listOf(Pixel(.3, .1), Pixel(.7, .1), Pixel(.3, .9), Pixel(.7, .9)),
            listOf(Pixel(.3, .9), Pixel(.3, .1), Pixel(.7, .9), Pixel(.7, .1)),
            listOf(Pixel(.7, .9), Pixel(.3, .9), Pixel(.7, .1), Pixel(.3, .1)),
            listOf(Pixel(.7, .1), Pixel(.7, .9), Pixel(.3, .1), Pixel(.3, .9))
        )[turn]
        val screen = listOf(Pixel(20.0, 20.0), Pixel(180.0, 20.0), Pixel(20.0, 180.0), Pixel(180.0, 180.0))
        screen.zip(expected).forEachIndexed { i, (point, want) ->
            assertPixel("S=$sensor D=$display cropped corner=$i", want, sampling.map(point))
        }
    }

    @Test fun offCenterOpticalRayAndNativeImageMeetAtTheSameLiteralPixel() {
        val width = if (turn % 2 == 0) 400 else 200
        val height = if (turn % 2 == 0) 200 else 400
        val preview = PreviewGeometry.create(1, 400, 200, width, height, sensor, display, 7)
        // Synthetic optical fixture: fx=fy=200, cx=200, cy=100; sensor marker=(100,70).
        val capture = CaptureGeometry(7, 123, "synthetic", null, "synthetic", LensLock.DIRECT_REAR,
            sensor, 400, 200, OpticsMetadata(4.0, 8.0, 4.0, 400, 200, RectD(0.0, 0.0, 400.0, 200.0)),
            RectD(0.0, 0.0, 400.0, 200.0), 1f, 1, 0, 0, 0, 1, RawCalibration(null, null), "test fixture")
        val deviceRay = listOf(CameraVector(-.5, .15, 1.0), CameraVector(.15, .5, 1.0),
            CameraVector(.5, -.15, 1.0), CameraVector(-.15, -.5, 1.0))[sensor / 90]
        val expected = listOf(Pixel(100.0, 70.0), Pixel(130.0, 100.0),
            Pixel(300.0, 130.0), Pixel(70.0, 300.0))[turn]
        val overlay = capture.projectNatural(deviceRay, preview)
        assertPixel("S=$sensor D=$display overlay", expected, overlay)
        assertPixel("S=$sensor D=$display native marker", Pixel(.25, .35),
            GlassSampling.viewToSampler(preview, actual).map(overlay!!))
        assertNull(capture.copy(sensorDegrees = null).projectNatural(deviceRay, preview))
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "sensor={0}, display={1}")
        fun rotations() = listOf(0, 90, 180, 270).flatMap { sensor ->
            listOf(0, 90, 180, 270).map { display -> arrayOf(sensor, display) }
        }
    }
}
