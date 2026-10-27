package local.skytrails.app.ar

/** Closing a rejected session may complete after pause or another open; its ticket then expires. */
class ReopenTicket(private val epoch:Long) {
    fun isCurrent(gate:CameraLifecycle) = gate.epoch==epoch && gate.state==CameraLifecycle.State.CLOSED
}

/** One recovery to ordinary preview for physical-output setup rejection, never a device retry loop. */
class PhysicalSetupFallback {
    private var used = false

    fun reject(gate:CameraLifecycle,epoch:Long,physical:Boolean,permission:Boolean,failure:Throwable?):ReopenTicket? {
        val rejectedConfiguration = failure==null || failure is IllegalArgumentException || failure is UnsupportedOperationException
        if(used || !physical || !permission || !rejectedConfiguration || !gate.accepts(epoch))return null
        used = true
        gate.stop()
        return ReopenTicket(gate.epoch)
    }
}
