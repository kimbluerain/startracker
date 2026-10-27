package local.skytrails.app.ui

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import local.skytrails.app.location.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RealtimeClockTest {
 @Test fun previewStillPublishesExpiryWithoutCameraStatusOrUserInteraction() = runTest {
  val start=Instant.parse("2026-10-23T00:00:00Z")
  val repository=LocationRepository()
  repository.acceptDeviceFix(DeviceFix(30.0,120.0,start.minusSeconds(899),15.0,"test"),ZoneId.of("Asia/Shanghai"))
  val preview=start.plusSeconds(7200)
  val state=MutableStateFlow(AppState(location=repository.state.value.selected,instant=preview,live=false,cameraPermission=true,locationPermission=true,realNow=start))
  val emitted=mutableListOf<AppState>();var recomputes=0
  val collector=backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){state.take(3).toList(emitted)}
  backgroundScope.launch { driveRealtimeClock(state,{start.plusMillis(testScheduler.currentTime)}) {recomputes++} }
  runCurrent();advanceTimeBy(2001);runCurrent()
  assertEquals(listOf(true,true,false),emitted.map{it.cameraEligible(it.realNow)})
  assertTrue(emitted.all{it.instant==preview&&!it.live})
  assertEquals(0,recomputes)
  collector.cancel()
 }
}
