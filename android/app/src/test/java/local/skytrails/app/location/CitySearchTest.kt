package local.skytrails.app.location
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
@OptIn(ExperimentalCoroutinesApi::class)
class CitySearchTest {
 private fun city(id:String)=CityEntry(id,id,emptyList(), "ZZ",0.0,0.0,"UTC",0)
 @Test fun queryTypedBeforeIndexReadyIsReplayed()=runTest {
  val search=CitySearch(backgroundScope,{error(it)})
  search.query("London")
  search.ready{listOf(city(it))};runCurrent()
  assertEquals("London",search.results.value.single().id)
 }
 @Test fun oldNoncancellableResultCannotOverwriteNewQueryOrClear()=runTest {
  val old=CompletableDeferred<Unit>();val search=CitySearch(backgroundScope,{error(it)})
  search.ready{q->if(q=="old")withContext(NonCancellable){old.await()};listOf(city(q))}
  search.query("old");runCurrent();search.query("new");runCurrent()
  assertEquals("new",search.results.value.single().id)
  old.complete(Unit);runCurrent();assertEquals("new",search.results.value.single().id)
  search.query("");runCurrent();assertTrue(search.results.value.isEmpty())
 }
}
