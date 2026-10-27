package local.skytrails.app

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import local.skytrails.app.location.*
import local.skytrails.app.ui.*
import local.skytrails.app.ui.renderer.CameraRuntime
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.*
import java.util.concurrent.atomic.AtomicReference

/** Synthetic state fixtures stay in the test Activity; they never inject a device GPS fix. */
class SkyStateRegressionTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 @Before fun controlRenderFrameClock() {
  // CameraScene intentionally awaits every display frame. Automatic clock advancement
  // cannot find idle; drive a bounded number of frames around each interaction instead.
  ui.mainClock.autoAdvance=false
 }
 private fun pumpUntil(timeoutMillis:Long=5000,condition:()->Boolean) {
  ui.waitUntil(timeoutMillis) {ui.mainClock.advanceTimeBy(32);condition()}
 }
 private fun frame(){ui.mainClock.advanceTimeBy(64);ui.waitForIdle()}
 @Test fun previewClockExpiryDisposesActualCameraAndPoseHosts() {
  val start=Instant.now();val clock=AtomicReference(start)
  val repository=LocationRepository()
  repository.acceptDeviceFix(DeviceFix(30.0,120.0,start.minusSeconds(899),15.0,"isolated-test"),ZoneId.of("Asia/Shanghai"))
  val preview=start.plusSeconds(7200)
  val state=MutableStateFlow(AppState(location=repository.state.value.selected,instant=preview,realNow=start,live=false,cameraPermission=true,locationPermission=true))
  val runtime=CameraRuntime();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
  try {
   ui.setContent {val s by state.collectAsState();SkyTheme(s.theme){ObserveScreen(s,null,runtime,{state.value=it(state.value)},{},{},{},{},{})}}
   pumpUntil{runtime.view!=null&&runtime.pose!=null}
   ui.onNodeWithTag("camera_host").assertExists()
   scope.launch{driveRealtimeClock(state,clock::get){error("Preview must not recompute")}}
   clock.set(start.plusSeconds(2))
   pumpUntil{runtime.view==null&&runtime.pose==null}
   ui.onNodeWithTag("camera_host").assertDoesNotExist()
   ui.onNodeWithText("定位已过期 · 点此更新").assertExists()
   assertEquals(preview,state.value.instant);assertFalse(state.value.live)
  } finally {scope.cancel()}
 }
 @Test fun citySelectionCannotRestorePhysicalHostAfterLeavingObserve() {
  lateinit var model:SkyViewModel
  val loadStart=SystemClock.elapsedRealtime()
  ui.runOnUiThread {
   model=ViewModelProvider(ui.activity)[SkyViewModel::class.java]
   assertEquals("local.skytrails.app",model.getApplication<Application>().packageName)
   model.select(ManualLocationInput("isolated onsite fixture","30","120","Asia/Shanghai",confirmedOnSite=true).selection())
   model.change{it.copy(cameraPermission=true)}
  }
  ui.setContent {SkyApp(model,{},{},{})}
  pumpUntil(10000){ui.onAllNodesWithTag("camera_host").fetchSemanticsNodes().isNotEmpty()}
  ui.onNodeWithTag("tab_PLACES").performClick();frame()
  try {
   // API30 standalone packaged-index parse measured 15.169s; only this one-time IO
   // deadline has margin. Camera host creation/disposal keep their existing bounds.
   pumpUntil(30000){model.cityReady.value||model.error.value!=null}
  } catch(e:Throwable) {
   throw AssertionError("City initialization failed: error=${model.error.value}, scopeActive=${model.viewModelScope.coroutineContext[Job]?.isActive}, elapsedMs=${SystemClock.elapsedRealtime()-loadStart}",e)
  }
  assertNull("City initialization error",model.error.value)
  assertTrue(model.cityReady.value)
  Log.i("SkyTrailsUiTest","cityIndexReadyMs=${SystemClock.elapsedRealtime()-loadStart}")
  ui.onNodeWithTag("city_search").performTextInput("Hangzhou");frame()
  pumpUntil{ui.onAllNodesWithTag("city_1808926").fetchSemanticsNodes().isNotEmpty()}
  ui.onNodeWithTag("city_1808926").performClick();frame()
  ui.onNodeWithTag("tab_OBSERVE").performClick();frame()
  ui.onNodeWithTag("camera_host").assertDoesNotExist()
  ui.onNodeWithText("计算位置 · 虚拟天空").assertExists()
  ui.runOnIdle {assertEquals(LocationSource.CITY_CENTER,model.state.value.location?.source);assertFalse(model.state.value.cameraEligible(model.state.value.realNow))}
 }
}
