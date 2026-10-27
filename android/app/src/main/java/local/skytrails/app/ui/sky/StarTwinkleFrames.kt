package local.skytrails.app.ui.sky

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A bounded visual pulse, separate from astronomy time, pose and camera preview frames. */
@Composable internal fun StarTwinkleFrames(enabled:Boolean,onFrame:(Float)->Unit) {
 val owner=LocalLifecycleOwner.current
 val callback by rememberUpdatedState(onFrame)
 DisposableEffect(owner,enabled) {
  // Native lifecycle scope keeps the visual pulse independent from composition/frame clocks.
  val job=if(enabled)owner.lifecycleScope.launch {
   owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
    while(isActive){callback(StarTwinkle.seconds());delay(StarTwinkle.FRAME_MILLIS)}
   }
  }else null
  onDispose{job?.cancel()}
 }
}
