package local.skytrails.app

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import local.skytrails.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PageThemeDisplayTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 @Test fun dataPageBackgroundAndSystemBarIconsFollowEffectiveTheme() {
  lateinit var model:SkyViewModel
  ui.runOnUiThread{model=ViewModelProvider(ui.activity)[SkyViewModel::class.java]}
  var systemDark by mutableStateOf(false)
  ui.mainClock.autoAdvance=false
  ui.setContent {
   val config=Configuration(ui.activity.resources.configuration).apply {
    uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())or if(systemDark)Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
   }
   CompositionLocalProvider(LocalConfiguration provides config){SkyApp(model,{},{},{})}
  }
  ui.waitUntil(15000){ui.mainClock.advanceTimeBy(64);model.settingsReady.value}
  data class Case(val mode:AppThemeMode,val darkSystem:Boolean,val darkIcons:Boolean)
  val cases=listOf(Case(AppThemeMode.SYSTEM,false,true),Case(AppThemeMode.SYSTEM,true,false),Case(AppThemeMode.LIGHT,true,true),Case(AppThemeMode.OUTDOOR,true,true),Case(AppThemeMode.DARK,false,false),Case(AppThemeMode.NIGHT,false,false))
  try {
   for(tab in listOf(AppTab.PLACES,AppTab.PLAN))for(case in cases) {
    ui.runOnIdle{systemDark=case.darkSystem;model.change{it.copy(tab=tab,theme=case.mode)}}
    ui.mainClock.advanceTimeBy(64);ui.waitForIdle()
    ui.runOnIdle {
     val controller=WindowCompat.getInsetsController(ui.activity.window,ui.activity.window.decorView)
     assertEquals("$tab ${case.mode} status icons",case.darkIcons,controller.isAppearanceLightStatusBars)
     assertEquals("$tab ${case.mode} navigation icons",case.darkIcons,controller.isAppearanceLightNavigationBars)
    }
    val image=ui.onRoot().captureToImage().toPixelMap()
    // An uncovered margin, away from text, samples the page rather than a control.
    val margin=image[(image.width*.015f).toInt(),(image.height*.3f).toInt()]
    if(case.darkIcons)assertTrue("$tab ${case.mode} must have a light margin",margin.red>.80f&&margin.green>.80f&&margin.blue>.80f)
    else assertTrue("$tab ${case.mode} must have a dark margin",margin.red<.60f&&margin.green<.60f&&margin.blue<.60f)
   }
   ui.runOnIdle{model.change{it.copy(tab=AppTab.OBSERVE,theme=AppThemeMode.LIGHT)}}
   ui.mainClock.advanceTimeBy(64);ui.waitForIdle()
   ui.runOnIdle{assertFalse(WindowCompat.getInsetsController(ui.activity.window,ui.activity.window.decorView).isAppearanceLightStatusBars)}
  } finally {ui.runOnIdle{model.change{it.copy(theme=AppThemeMode.SYSTEM,tab=AppTab.OBSERVE)}}}
 }
}
