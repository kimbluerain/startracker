package local.skytrails.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import local.skytrails.app.ui.*
import local.skytrails.app.ui.renderer.*
import org.junit.Rule
import org.junit.Test

class SkyDialogLayoutTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 @Test fun landscapeLargeTextKeepsPitchAndFooterReachableAndButtonsVisible() {
  ui.runOnUiThread{ui.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
  ui.waitUntil(5000){ui.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
  ui.setContent {
   val density=LocalDensity.current
   CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) {
    SkyTheme(AppThemeMode.DARK){var open by remember{mutableStateOf(true)};if(open)CalibrationDialog(remember{CameraRuntime()}){open=false}}
   }
  }
  ui.onNodeWithTag("calibration_pitch").performScrollTo().assertIsDisplayed().performSemanticsAction(SemanticsActions.SetProgress){it(12f)}
  ui.onNodeWithTag("calibration_end").performScrollTo().assertIsDisplayed()
  ui.onNodeWithTag("calibration_save").assertIsDisplayed()
  ui.onNodeWithTag("calibration_dismiss").assertIsDisplayed().performClick()
  ui.onNodeWithTag("calibration_body").assertDoesNotExist()
 }
}
