package local.skytrails.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import local.skytrails.app.core.*
import local.skytrails.app.core.Target
import local.skytrails.app.location.*
import java.time.*

 data class TimedEvent(val target:SkyTarget,val event:SkyEvent)
 data class SkyCalculation(val site:Site,val at:Instant,val sun:SkyDirection,val moon:SkyDirection,val galaxy:GalacticSky,
    val events:List<TimedEvent>,val sunPath:List<SkyDirection>,val moonPath:List<SkyDirection>,val stars:StarSky?=null)

class SkyViewModel(app:Application):AndroidViewModel(app) {
    val locations=LocationRepository()
    private val mutable=MutableStateFlow(AppState(virtualSky=true))
    val state=mutable.asStateFlow()
    private val calc=MutableStateFlow<SkyCalculation?>(null)
    val calculation=calc.asStateFlow()
    val error=MutableStateFlow<String?>(null)
    val favorites=MutableStateFlow<List<Favorite>>(emptyList())
    private val citySearch=CitySearch(viewModelScope){error.value=it}
    val cities=citySearch.results
    val cityReady=MutableStateFlow(false)
    val startupLocation=StartupLocationRequest()
    val settingsReady=MutableStateFlow(false)
    private val settingsStore=UserSettingsStore(app.skyTrailsSettingsDataStore)
    private val settingsSession=SettingsSession(viewModelScope,settingsStore::read,settingsStore::write,
        {saved->mutable.value=mutable.value.copy(theme=saved.theme,reduceTransparency=saved.reduceTransparency,layers=saved.layers,starLayers=saved.starLayers)},
        {error.value=it},{settingsReady.value=true})
    private val favoriteStore=FavoriteStore(app.skyTrailsFavoriteDataStore)
    private val service=AstronomyEngineAdapter()
    private val galaxy=MilkyWayGeometry()
    private var calculationJob:Job?=null
    private val requests=LatestRequest()
    private var cachedDay:Pair<Site,LocalDate>?=null
    private var cachedEvents=emptyList<TimedEvent>()
    private var cachedSun=emptyList<SkyDirection>()
    private var cachedMoon=emptyList<SkyDirection>()
    init {
        viewModelScope.launch { locations.state.collect { location ->
            mutable.value=mutable.value.withSelectedLocation(location.selected)
            if(location.error!=null) error.value=location.error
            recompute()
        } }
        viewModelScope.launch { favoriteStore.favorites.catch { error.value="收藏读取失败，原数据已保留：${it.message}" }.collect { favorites.value=it } }
        viewModelScope.launch { runCatching { withContext(Dispatchers.IO) { CityIndex.readGzip(app.assets.open("cities15000.data")) } }
            .onSuccess { index->citySearch.ready { text->withContext(Dispatchers.Default){index.search(text)} };cityReady.value=true }.onFailure { error.value="离线城市暂不可用，可手动输入" } }
        viewModelScope.launch { driveRealtimeClock(mutable,onTimeChanged=::recompute) }
    }
    fun change(block:(AppState)->AppState) {
        val before=mutable.value;val after=block(before);mutable.value=after
        if(before.theme!=after.theme || before.reduceTransparency!=after.reduceTransparency || before.layers!=after.layers || before.starLayers!=after.starLayers)
            settingsSession.changed(UserSettings(after.theme,after.reduceTransparency,after.layers,after.starLayers))
        if(before.location!=after.location || before.instant!=after.instant)recompute()
    }
    fun changeSettings(block:(AppState)->AppState) {
        mutable.value=block(mutable.value)
        settingsSession.changed(UserSettings(mutable.value.theme,mutable.value.reduceTransparency,mutable.value.layers,mutable.value.starLayers))
    }
    fun preview(at:Instant) { if(at.atZone(mutable.value.location?.site?.zone ?: ZoneId.systemDefault()).year !in 2000..2050) { error.value="日期支持 2000—2050 年";return };change { it.preview(at) } }
    fun now()=change { it.now(Instant.now()) }
    fun select(place:SelectedLocation) { locations.select(place) }
    fun manual(input:ManualLocationInput):Boolean {
        val result=runCatching { input.selection() }
        result.onSuccess(::select).onFailure { error.value=it.message };return result.isSuccess
    }
    fun city(city:CityEntry) { runCatching { city.selection() }.onSuccess(::select).onFailure { error.value="此系统不支持 ${city.zoneId}，请在手动地点中明确选择可用时区" } }
    fun search(text:String)=citySearch.query(text)
    fun favorite() { val place=state.value.location ?: return;viewModelScope.launch {
        runCatching { favoriteStore.save(Favorite("${place.site.latitude}/${place.site.longitude}/${place.site.zone.id}",place)) }
            .onFailure { error.value="收藏未保存：${it.message}" }
    } }
    fun remove(f:Favorite) { viewModelScope.launch { runCatching { favoriteStore.remove(f.id) }.onFailure { error.value="删除未完成：${it.message}" } } }
    private fun recompute() {
        val s=state.value;val site=s.location?.site
        val token=requests.begin();calculationJob?.cancel()
        if(site==null) { calc.value=null;return }
        val date=s.instant.atZone(site.zone).toLocalDate()
        if(date.year !in 2000..2050) { calc.value=null;error.value="日期支持 2000—2050 年";return }
        calculationJob=viewModelScope.launch {
            try {
                val key=site to date
                val dayData=if(key==cachedDay) Triple(cachedEvents,cachedSun,cachedMoon) else withContext(Dispatchers.Default) {
                    val day=dayWindow(date,site.zone);val duration=Duration.between(day.start,day.endExclusive).seconds
                    val times=(0..144).map { day.start.plusSeconds(duration*it/144) }
                    Triple((service.events(Target.SUN,site,date).map { TimedEvent(SkyTarget.SUN,it) }+service.events(Target.MOON,site,date).map { TimedEvent(SkyTarget.MOON,it) }).sortedBy { it.event.instant },
                        times.map { service.position(Target.SUN,site,it,Atmosphere.AIRLESS) },times.map { service.position(Target.MOON,site,it,Atmosphere.AIRLESS) })
                }
                val result=withContext(Dispatchers.Default) { SkyCalculation(site,s.instant,service.position(Target.SUN,site,s.instant,Atmosphere.AIRLESS),service.position(Target.MOON,site,s.instant,Atmosphere.AIRLESS),galaxy.sky(site,s.instant),dayData.first,dayData.second,dayData.third,StarCatalog.sky(site,s.instant)) }
                if(requests.accepts(token)) { cachedDay=key;cachedEvents=dayData.first;cachedSun=dayData.second;cachedMoon=dayData.third;calc.value=result }
            } catch(e:CancellationException) { throw e } catch(e:Exception) { if(requests.accepts(token)) {calc.value=null;error.value="计算暂不可用：${e.message}"} }
        }
    }
}
