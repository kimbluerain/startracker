package local.skytrails.app.ar

/** Pure admission gate used by the real Camera2 adapter, including all late callbacks. */
class CameraLifecycle {
    enum class State { CLOSED, OPENING, CONFIGURING, RUNNING }
    @Volatile var state=State.CLOSED; private set
    @Volatile var epoch=0L; private set
    @Synchronized fun start(resumed:Boolean,permission:Boolean,surface:Boolean):Long? {
        if(!resumed || !permission || !surface || state!=State.CLOSED) return null
        epoch++; state=State.OPENING; return epoch
    }
    @Synchronized fun stop() {epoch++; state=State.CLOSED}
    @Synchronized fun opened(epoch:Long):Boolean {
        if(this.epoch!=epoch || state!=State.OPENING)return false
        state=State.CONFIGURING;return true
    }
    @Synchronized fun configured(epoch:Long):Boolean {
        if(this.epoch!=epoch || state!=State.CONFIGURING)return false
        state=State.RUNNING;return true
    }
    @Synchronized fun accepts(epoch:Long)=this.epoch==epoch && state!=State.CLOSED
    @Synchronized fun fail(epoch:Long) {if(this.epoch==epoch)stop()}
}
