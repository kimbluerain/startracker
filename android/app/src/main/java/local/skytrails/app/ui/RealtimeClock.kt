package local.skytrails.app.ui

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/** Real location age is independent from the requested astronomy Instant. */
internal suspend fun driveRealtimeClock(state:MutableStateFlow<AppState>,now:()->Instant=Instant::now,onTimeChanged:()->Unit) {
 while(currentCoroutineContext().isActive) {
  delay(1000)
  val before=state.value
  val after=before.tick(now())
  state.value=after
  if(after.instant!=before.instant) onTimeChanged()
 }
}
