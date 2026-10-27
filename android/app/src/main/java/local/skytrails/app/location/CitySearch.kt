package local.skytrails.app.location
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

/** Remembers an early query and rejects late results even from non-cancellable searches. */
class CitySearch(private val scope:CoroutineScope,private val onError:(String)->Unit) {
    val results=MutableStateFlow<List<CityEntry>>(emptyList())
    private var current=""
    private var version=0L
    private var job:Job?=null
    private var search:(suspend(String)->List<CityEntry>)?=null
    fun ready(search:suspend(String)->List<CityEntry>) {this.search=search;query(current)}
    fun query(text:String) {
        current=text;val token=++version;job?.cancel();results.value=emptyList()
        val lookup=search ?: return
        if(text.isBlank()) return
        job=scope.launch {
            try {val found=lookup(text);if(token==version)results.value=found}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {if(token==version)onError("城市搜索暂不可用，可手动输入")}
        }
    }
}
