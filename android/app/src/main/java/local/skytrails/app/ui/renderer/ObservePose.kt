package local.skytrails.app.ui.renderer

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import local.skytrails.app.ar.*
import local.skytrails.app.ui.AppState

/** One observation-session sensor owner; the camera and virtual renderer are replaceable clients. */
@Composable fun rememberObservePose(runtime:CameraRuntime,state:AppState):PoseFrame? {
 val context=LocalContext.current;val owner=LocalLifecycleOwner.current
 val adapter=remember(runtime){PoseAdapter(context)}
 val place=state.poseLocation(state.realNow)?.site
 val site=place?.let{PhysicalSite(it.latitude,it.longitude,it.elevationMeters+it.eyeHeightMeters)}
 val displays=remember{context.getSystemService(DisplayManager::class.java)}
 var display by remember{mutableIntStateOf(0)}
 var frame by remember{mutableStateOf<PoseFrame?>(null)}
 SideEffect{runtime.pose=adapter;runtime.displayDegrees=display}
 DisposableEffect(adapter,owner,site) {
  fun start(){if(site!=null)adapter.start(site) else adapter.stop()}
  val observer=object:DefaultLifecycleObserver {
   override fun onStart(owner:LifecycleOwner)=start()
   override fun onStop(owner:LifecycleOwner){adapter.stop()}
  }
  owner.lifecycle.addObserver(observer)
  onDispose{owner.lifecycle.removeObserver(observer);adapter.stop()}
 }
 DisposableEffect(runtime,adapter) {runtime.pose=adapter;onDispose{adapter.stop();if(runtime.pose===adapter)runtime.pose=null}}
 DisposableEffect(displays) {
  fun update(){display=when(displays.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.rotation){Surface.ROTATION_90->90;Surface.ROTATION_180->180;Surface.ROTATION_270->270;else->0}}
  val listener=object:DisplayManager.DisplayListener {override fun onDisplayAdded(id:Int)=update();override fun onDisplayRemoved(id:Int)=update();override fun onDisplayChanged(id:Int)=update()}
  update();displays.registerDisplayListener(listener,Handler(Looper.getMainLooper()))
  onDispose{displays.unregisterDisplayListener(listener)}
 }
 LaunchedEffect(adapter){while(true){withFrameNanos{frame=adapter.frame.get()}}}
 return frame
}
