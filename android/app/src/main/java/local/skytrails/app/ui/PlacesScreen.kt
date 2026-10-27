package local.skytrails.app.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import local.skytrails.app.location.*
import java.time.Instant

@Composable fun PlacesScreen(s:AppState,model:SkyViewModel,requestLocation:()->Unit,stopLocation:()->Unit) {
 val keyboard=LocalSoftwareKeyboardController.current;val focus=LocalFocusManager.current
 var search by remember {mutableStateOf("")};var showManual by remember {mutableStateOf(false)};var selectedDetails by remember {mutableStateOf(false)}
 val cities by model.cities.collectAsState();val favorites by model.favorites.collectAsState();val ready by model.cityReady.collectAsState()
 val locationState by model.locations.state.collectAsState();val theme=pageTheme(s);val night=theme==AppThemeMode.NIGHT
 val light=usesDarkSystemIcons(theme)
 val opaque=s.reduceTransparency||s.theme==AppThemeMode.OUTDOOR
 val color=if(night)NightRed else if(light)Ink else MoonSilver
 val accent=if(night)NightRed else if(light)androidx.compose.ui.graphics.Color(0xFF71542E) else SunGold
 Box(Modifier.fillMaxSize()) {
  local.skytrails.app.ui.sky.SkyBackdrop(null,null,galaxy=true,night=night,modifier=Modifier.fillMaxSize(),exposure=.65f)
  Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Black.copy(alpha=.15f),androidx.compose.ui.graphics.Color.Black.copy(alpha=.1f),androidx.compose.ui.graphics.Color(0xFF101216).copy(alpha=.8f)))))
  if(light)Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFFF2EFEA).copy(alpha=if(theme==AppThemeMode.OUTDOOR)1f else .87f)))
  PageGlassContent(s) {
   Column(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom=76.dp).verticalScroll(rememberScrollState()).statusBarsPadding().padding(start=22.dp,end=22.dp,top=38.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
    Column {Text("地点",fontSize=38.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-1).sp);Spacer(Modifier.height(9.dp));Text("选择你想看的天空",fontSize=16.sp,color=color.copy(alpha=.7f))}
    FrostedSurface(Modifier.fillMaxWidth(),opaque,night,23.dp) {
     TextField(search,{search=it;model.search(it)},placeholder={Text("搜索城市",color=color.copy(alpha=.65f))},leadingIcon={SkyGlyph(SkyIcon.SEARCH,Modifier.size(20.dp),color.copy(alpha=.8f))},singleLine=true,
      colors=TextFieldDefaults.colors(focusedContainerColor=androidx.compose.ui.graphics.Color.Transparent,unfocusedContainerColor=androidx.compose.ui.graphics.Color.Transparent,focusedIndicatorColor=androidx.compose.ui.graphics.Color.Transparent,unfocusedIndicatorColor=androidx.compose.ui.graphics.Color.Transparent),modifier=Modifier.fillMaxWidth().testTag("city_search"))
    }
    if(search.isNotBlank()) {
     FrostedSurface(Modifier.fillMaxWidth(),opaque,night) {Column(Modifier.padding(10.dp)) {
      if(cities.isEmpty())Text(if(ready)"未找到该名称，可手动设置坐标" else "正在加载离线城市…",Modifier.padding(10.dp),fontSize=13.sp)
      cities.forEach {city->TextButton(onClick={stopLocation();model.city(city);search="";model.search("");focus.clearFocus();keyboard?.hide()},modifier=Modifier.fillMaxWidth().testTag("city_${city.id}")){Column(Modifier.fillMaxWidth()){Text("${city.name} · ${city.country}",fontSize=17.sp,color=color);Text("城市中心 · ${city.zoneId}",fontSize=12.sp,color=color.copy(alpha=.6f))}}}
     }}
    }
    FrostedSurface(Modifier.fillMaxWidth(),opaque,night) {
     Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
      SkyGlyph(SkyIcon.LOCATION,Modifier.size(29.dp),accent);Spacer(Modifier.width(16.dp))
      Column(Modifier.weight(1f)) {Text("手机位置",fontSize=17.sp,fontWeight=FontWeight.Medium);Spacer(Modifier.height(5.dp));Text(when(locationState.status){LocationStatus.LOCATING->"正在获取位置…";LocationStatus.PERMISSION_DENIED->"未授权 · 可手动选择";else->if(s.location?.source==LocationSource.DEVICE)"已获得设备位置" else "使用当前位置"},fontSize=12.sp,color=color.copy(alpha=.6f))}
      TextButton(onClick=requestLocation,modifier=Modifier.heightIn(min=48.dp).testTag("request_location"),colors=ButtonDefaults.textButtonColors(containerColor=accent.copy(alpha=.14f))) {Text("更新",fontSize=13.sp)}
     }
    }
    s.location?.let {place->Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable{selectedDetails=true},verticalAlignment=Alignment.CenterVertically) {
     Text("当前 · ${place.name}",Modifier.weight(1f),fontSize=13.sp,color=color.copy(alpha=.75f));Text(if(s.physicalEligible(s.realNow))"现场" else "模拟",fontSize=11.sp,color=accent);Spacer(Modifier.width(7.dp));SkyGlyph(SkyIcon.CHEVRON,Modifier.size(15.dp),color.copy(alpha=.6f))
    }}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
     Text("收藏地点",Modifier.weight(1f),fontSize=19.sp,fontWeight=FontWeight.SemiBold)
     IconButton(onClick={if(s.location!=null)model.favorite()else showManual=true},modifier=Modifier.testTag("save_favorite").semantics{contentDescription="收藏当前地点"}){SkyGlyph(SkyIcon.PLUS,color=accent)}
    }
    FrostedSurface(Modifier.fillMaxWidth(),opaque,night) {Column(Modifier.padding(horizontal=16.dp)) {
     if(favorites.isEmpty())Column(Modifier.padding(vertical=22.dp)) {Text("收藏一片喜欢的天空",fontSize=16.sp);Spacer(Modifier.height(6.dp));Text("选择地点后，轻点上方 + 保存。",fontSize=12.sp,color=color.copy(alpha=.6f))}
     favorites.forEachIndexed {i,f->
      if(i>0)HorizontalDivider(color=color.copy(alpha=.17f))
      Row(Modifier.fillMaxWidth().heightIn(min=76.dp),verticalAlignment=Alignment.CenterVertically) {
       Column(Modifier.weight(1f).clickable{stopLocation();model.select(f.location)}.padding(vertical=12.dp)) {Text(f.location.name,fontSize=18.sp,fontWeight=FontWeight.Medium);Spacer(Modifier.height(4.dp));Text(f.location.site.zone.id,fontSize=12.sp,color=color.copy(alpha=.6f))}
       IconButton(onClick={model.remove(f)},modifier=Modifier.semantics{contentDescription="取消收藏 ${f.location.name}"}){SkyGlyph(SkyIcon.STAR,Modifier.size(19.dp),accent)}
       SkyGlyph(SkyIcon.CHEVRON,Modifier.size(17.dp),color.copy(alpha=.6f))
      }
     }
    }}
    FrostedSurface(Modifier.fillMaxWidth().clickable{showManual=true}.testTag("manual_open"),opaque,night,23.dp) {
     Row(Modifier.fillMaxWidth().padding(horizontal=18.dp).heightIn(min=68.dp),verticalAlignment=Alignment.CenterVertically) {
      SkyGlyph(SkyIcon.CROSSHAIR,Modifier.size(25.dp),color.copy(alpha=.8f));Spacer(Modifier.width(16.dp));Text("手动输入经纬度",Modifier.weight(1f),fontSize=16.sp);SkyGlyph(SkyIcon.CHEVRON,Modifier.size(17.dp),color.copy(alpha=.65f))
     }
    }
   }
  }
 }
 if(selectedDetails)s.location?.let{place->AlertDialog(onDismissRequest={selectedDetails=false},title={Text(place.name)},text={Text(locationDescription(place))},confirmButton={TextButton(onClick={selectedDetails=false}){Text("完成")}})}
 if(showManual)ManualDialog(onDismiss={showManual=false},onChoose={input->stopLocation();if(model.manual(input)){focus.clearFocus();keyboard?.hide();showManual=false}})
}
fun locationDescription(p:SelectedLocation):String=buildString {
 append("WGS84 ${"%.5f".format(java.util.Locale.ROOT,p.site.latitude)}, ${"%.5f".format(java.util.Locale.ROOT,p.site.longitude)}\n")
 append("${p.site.zone.id} · ${when(p.zoneSource){ZoneSource.DEVICE->"设备时区（请核对）";ZoneSource.CITY->"城市时区";ZoneSource.EXPLICIT->"手动时区"}}\n")
 append(if(p.elevationSource==ElevationSource.ASSUMED_ZERO)"海拔按 0 米假设" else "海拔 ${p.site.elevationMeters} 米");append(" · 离地 ${p.site.eyeHeightMeters} 米")
 p.fixAt?.let {append("\n定位 ${p.ageAt(Instant.now())!!.seconds/60} 分钟前");p.accuracyMeters?.let{m->append(" · 系统位置精度约 ${m.toInt()} 米")}}
 if(p.source!=LocationSource.DEVICE)append(if(p.confirmedManualOnSite)"\n已明确确认为现场位置" else "\n异地模拟；不会开启现场相机")
}
@Composable fun ManualDialog(onDismiss:()->Unit,onChoose:(ManualLocationInput)->Unit) {
 var name by remember {mutableStateOf("")};var lat by remember {mutableStateOf("")};var lon by remember {mutableStateOf("")};var zone by remember {mutableStateOf("")};var elevation by remember {mutableStateOf("")};var eye by remember {mutableStateOf("0")};var onsite by remember {mutableStateOf(false)};var pair by remember {mutableStateOf("")};var error by remember {mutableStateOf<String?>(null)}
 AlertDialog(onDismissRequest=onDismiss,title={Text("手动观测地点")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  OutlinedTextField(name,{name=it},label={Text("地点名称（可选）")},modifier=Modifier.testTag("manual_name"))
  OutlinedTextField(pair,{pair=it},label={Text("粘贴 WGS84 纬度,经度")})
  TextButton(onClick={runCatching { ManualLocationInput.parseCoordinatePair(pair) }.onSuccess{lat=it.first.toString();lon=it.second.toString();error=null}.onFailure{error=it.message}}){Text("填入坐标（不交换顺序）")}
  OutlinedTextField(lat,{lat=it},label={Text("纬度 −90 至 90")},modifier=Modifier.testTag("manual_latitude"))
  OutlinedTextField(lon,{lon=it},label={Text("经度 −180 至 180")},modifier=Modifier.testTag("manual_longitude"))
  OutlinedTextField(zone,{zone=it},label={Text("IANA 时区，如 Asia/Shanghai")},modifier=Modifier.testTag("manual_zone"))
  OutlinedTextField(elevation,{elevation=it},label={Text("海拔米（留空按 0 米假设）")})
  OutlinedTextField(eye,{eye=it},label={Text("离地高度米")})
  Row(verticalAlignment=Alignment.CenterVertically){Checkbox(onsite,{onsite=it},modifier=Modifier.testTag("manual_onsite"));Text("我确认这些坐标是当前现场位置")}
  Text("仅接受 WGS84，不接受 GCJ-02 / BD-09。",fontSize=12.sp)
  error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
 }},confirmButton={TextButton(onClick={val input=ManualLocationInput(name,lat,lon,zone,elevation,eye,onsite);runCatching{input.selection()}.onSuccess{onChoose(input)}.onFailure{error=it.message}},modifier=Modifier.testTag("manual_save")){Text("使用此地点")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}
