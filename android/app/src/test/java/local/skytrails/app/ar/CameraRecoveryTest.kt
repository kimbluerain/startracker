package local.skytrails.app.ar

import org.junit.Assert.*
import org.junit.Test

class CameraRecoveryTest {
    @Test fun freshOutputWithoutPhysicalMetadataRemainsVisibleButHasNoProjection() {
        val frames = PreviewFrames<String>()
        frames.recordFrame(4, 100)
        val fresh = frames.frame(4, 100)
        assertNotNull("Current-session camera image must not require optional physical metadata", fresh)
        assertEquals(4L, fresh!!.epoch)
        assertNull("Missing physical metadata must not enable AR", fresh.geometry)
        assertNull(frames.frame(5, 100))
        assertNull(frames.frame(4, 99))
        frames.clear()
        assertNull(frames.frame(4, 100))
    }

    @Test fun oldGeometryCannotBeAppliedToNewSessionOrAnotherCameraImage() {
        val frames = PreviewFrames<String>()
        frames.recordFrame(1, 100)
        frames.putGeometry(1, 100, "old optics")
        frames.recordFrame(2, 200)
        assertNull(frames.frame(2, 200)!!.geometry)
        assertNull(frames.frame(2, 100))
        frames.putGeometry(2, 200, "current optics")
        assertEquals("current optics", frames.frame(2, 200)!!.geometry)
    }

    @Test fun missingGeometryDoesNotMakeEvidenceCacheUnbounded() {
        val frames = PreviewFrames<String>(2)
        frames.recordFrame(4, 100); frames.recordFrame(4, 200); frames.recordFrame(4, 300)
        assertNull(frames.frame(4, 100))
        assertNotNull(frames.frame(4, 300))
    }

    private fun configuring(gate:CameraLifecycle):Long {
        val epoch = gate.start(true, true, true)!!
        assertTrue(gate.opened(epoch))
        return epoch
    }

    @Test fun synchronousAndCallbackPhysicalRejectBothAllowOneOrdinaryPreviewRecovery() {
        for (failure in listOf(null, IllegalArgumentException("unsupported physical output"), UnsupportedOperationException("session combination"))) {
            val gate = CameraLifecycle()
            val fallback = PhysicalSetupFallback()
            val epoch = configuring(gate)
            val ticket = fallback.reject(gate, epoch, true, true, failure)
            assertNotNull(ticket)
            assertFalse(gate.accepts(epoch))
            assertTrue(ticket!!.isCurrent(gate))
            val ordinary = configuring(gate)
            assertNull(fallback.reject(gate, ordinary, false, true, null))
            assertNull("No retry loop even if a rejected physical configuration is mistakenly offered again", fallback.reject(gate, ordinary, true, true, failure))
            assertTrue(gate.accepts(ordinary))
        }
    }

    @Test fun permissionLossAndDeviceStateErrorsNeverTriggerLensFallback() {
        for (failure in listOf(SecurityException("permission"), IllegalStateException("camera closed"), Exception("device disconnected"))) {
            val gate = CameraLifecycle(); val epoch = configuring(gate)
            assertNull(PhysicalSetupFallback().reject(gate, epoch, true, true, failure))
            assertTrue(gate.accepts(epoch)) // Caller takes its normal terminal error/close path.
        }
        val gate = CameraLifecycle(); val epoch = configuring(gate)
        assertNull(PhysicalSetupFallback().reject(gate, epoch, true, false, null))
        assertTrue(gate.accepts(epoch))
    }

    @Test fun lateRejectOrLateCloseCannotReopenAfterPauseOrNewSession() {
        val gate = CameraLifecycle(); val first = configuring(gate)
        gate.stop()
        val fallback = PhysicalSetupFallback()
        assertNull(fallback.reject(gate, first, true, true, null))
        val current = configuring(gate)
        val ticket = fallback.reject(gate, current, true, true, IllegalArgumentException())!!
        gate.stop()
        assertFalse(ticket.isCurrent(gate))
        configuring(gate)
        assertFalse(ticket.isCurrent(gate))
    }
}
