package local.skytrails.app.ar

/** A capture-start/output timestamp match proves a new image, independently of lens metadata. */
data class PreviewFrame<T>(val epoch:Long,val timestampNs:Long,val geometry:T?)

class PreviewFrames<T>(capacity:Int=12) {
    private val images = FrameGeometryCache<Unit>(capacity)
    private val optics = FrameGeometryCache<T>(capacity)

    @Synchronized fun recordFrame(epoch:Long,timestampNs:Long) {
        images.put(epoch,timestampNs,Unit)
    }

    @Synchronized fun putGeometry(epoch:Long,timestampNs:Long,geometry:T) {
        optics.put(epoch,timestampNs,geometry)
    }

    @Synchronized fun frame(epoch:Long,timestampNs:Long):PreviewFrame<T>? {
        if(images.get(epoch,timestampNs)==null)return null
        return PreviewFrame(epoch,timestampNs,optics.get(epoch,timestampNs))
    }

    @Synchronized fun clear() { images.clear(); optics.clear() }
}
