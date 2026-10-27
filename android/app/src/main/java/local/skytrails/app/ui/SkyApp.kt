package local.skytrails.app.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.*
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import local.skytrails.app.ui.renderer.CameraRuntime

@Composable fun SkyApp(model:SkyViewModel,requestCamera:()->Unit,requestLocation:()->Unit,stopLocation:()->Unit) {
 val settingsReady by model.settingsReady.collectAsStateWithLifecycle()
 if(!settingsReady) {Box(Modifier.fillMaxSize().background(Color(0xFF101216)).testTag("settings_loading"));return}
 val s by model.state.collectAsStateWithLifecycle();val computed by model.calculation.collectAsStateWithLifecycle()
 val c=computed?.takeIf{it.site==s.location?.site && (it.at==s.instant || (s.live && kotlin.math.abs(java.time.Duration.between(it.at,s.instant).seconds)<2))}
 val error by model.error.collectAsStateWithLifecycle();val runtime=remember{CameraRuntime()};val backdrop=remember{FrostedBackdrop()}
 var date by remember{mutableStateOf(false)};var settings by remember{mutableStateOf(false)}
 val effectiveTheme=pageTheme(s)
 SkyTheme(s.theme) {
  val activity=LocalActivity.current;val view=LocalView.current
  SideEffect{activity?.let{WindowCompat.getInsetsController(it.window,view).apply{isAppearanceLightStatusBars=usesDarkSystemIcons(effectiveTheme);isAppearanceLightNavigationBars=usesDarkSystemIcons(effectiveTheme)}}}
  CompositionLocalProvider(LocalFrostedBackdrop provides backdrop) {
  Box(Modifier.fillMaxSize().background(if(usesDarkSystemIcons(effectiveTheme))Color(0xFFF2EFEA)else Color(0xFF101216)).imePadding()) {
   Box(Modifier.fillMaxSize().then(if(settings)Modifier.semantics{hideFromAccessibility()}else Modifier)) {
   when(s.tab) {
    AppTab.OBSERVE->ObserveScreen(s,c,runtime,model::change,model::preview,model::now,{date=true},{settings=true},requestCamera,hudVisible=!settings)
    AppTab.PLAN->PlanScreen(s,c,model::change,model::preview,model::now,{date=true})
    AppTab.PLACES->PlacesScreen(s,model,requestLocation,stopLocation)
   }
   if(!settings && (s.tab!=AppTab.OBSERVE || !s.controlsHidden)) {
    val mask=if(s.tab==AppTab.OBSERVE)Modifier.glassMask(runtime,"navigation",36.dp) else Modifier
    PageGlassContent(s) {
     FrostedSurface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal=28.dp,vertical=8.dp).widthIn(max=520.dp).fillMaxWidth().then(mask),s.reduceTransparency||s.theme==AppThemeMode.OUTDOOR,s.theme==AppThemeMode.NIGHT,36.dp) {
      Row(Modifier.padding(4.dp).testTag("bottom_navigation")) {
       listOf(Triple(AppTab.OBSERVE,"观测",SkyIcon.ORBIT),Triple(AppTab.PLAN,"计划",SkyIcon.CALENDAR),Triple(AppTab.PLACES,"地点",SkyIcon.PIN)).forEach {(tab,label,icon)->
        val active=s.tab==tab
        val accent=MaterialTheme.colorScheme.primary
        val foreground=MaterialTheme.colorScheme.onSurface
        Column(Modifier.weight(1f).heightIn(min=48.dp).clip(RoundedCornerShape(30.dp)).background(if(active)accent.copy(alpha=.10f) else Color.Transparent)
         .clickable{model.change{it.copy(tab=tab)}}.testTag("tab_${tab.name}").semantics{selected=active;role=Role.Tab},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
         SkyGlyph(icon,Modifier.size(23.dp),if(active)accent else foreground.copy(alpha=.8f));Spacer(Modifier.height(3.dp));Text(label,fontSize=11.sp,color=if(active)accent else foreground.copy(alpha=.8f))
        }
       }
      }
     }
    }
   }
   }
   if(settings)SettingsDialog(s,model::changeSettings,runtime){settings=false}
  }
  }
  if(date)DateTimeDialog(s,{date=false},model::preview)
  if(error!=null)AlertDialog(onDismissRequest={model.error.value=null},title={Text("提示")},text={Text(error!!)},confirmButton={TextButton(onClick={model.error.value=null}){Text("知道了")}})
 }
}
