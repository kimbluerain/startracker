package local.skytrails.app.location

/** Session intent survives Activity recreation via its ViewModel owner. Main-thread only. */
class StartupLocationRequest {
    private var attempted=false
    private var explicit=false
    fun requestExplicit() { explicit=true }
    fun denied() { explicit=false }
    fun take(started:Boolean, granted:Boolean, selected:SelectedLocation?):Boolean {
        if(!started || !granted) return false
        if(explicit) { explicit=false;attempted=true;return true }
        if(attempted) return false
        attempted=true
        return selected==null
    }
}
