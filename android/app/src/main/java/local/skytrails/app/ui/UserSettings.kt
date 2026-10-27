package local.skytrails.app.ui

import android.content.Context
import local.skytrails.app.core.StarCatalog
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first

val Context.skyTrailsSettingsDataStore by preferencesDataStore(name="sky_trails_settings")
data class UserSettings(val theme:AppThemeMode=AppThemeMode.SYSTEM,val reduceTransparency:Boolean=false,val layers:SkyLayers=SkyLayers(),val starLayers:StarLayers=StarLayers())
class UserSettingsStore(private val data:DataStore<Preferences>) {
    suspend fun read():UserSettings {
        val p=data.data.first()
        return UserSettings(AppThemeMode.entries.firstOrNull{it.name==p[THEME]} ?: AppThemeMode.SYSTEM,p[REDUCED] ?: false,SkyLayers(p[SUN] ?: true,p[MOON] ?: true,p[GALAXY] ?: true),
            StarLayers(p[STARS] ?: true,p[LINES] ?: true,p[NAMES] ?: true,(p[OBJECTS] ?: StarCatalog.ids).intersect(StarCatalog.ids),p[MASTER_STARS] ?: true,p[TWINKLE] ?: false))
    }
    suspend fun write(value:UserSettings) { data.edit {
        it[THEME]=value.theme.name;it[REDUCED]=value.reduceTransparency
        it[SUN]=value.layers.sun;it[MOON]=value.layers.moon;it[GALAXY]=value.layers.galaxy
        it[TWINKLE]=value.starLayers.twinkle;it[MASTER_STARS]=value.starLayers.masterVisible;it[STARS]=value.starLayers.stars;it[LINES]=value.starLayers.lines;it[NAMES]=value.starLayers.names;it[OBJECTS]=value.starLayers.enabled.intersect(StarCatalog.ids)
    } }
    private companion object {
        val TWINKLE=booleanPreferencesKey("star_twinkle")
        val MASTER_STARS=booleanPreferencesKey("layer_stars")
        val STARS=booleanPreferencesKey("catalog_stars")
        val LINES=booleanPreferencesKey("constellation_lines")
        val NAMES=booleanPreferencesKey("constellation_names")
        val OBJECTS=stringSetPreferencesKey("constellation_objects")
        val SUN=booleanPreferencesKey("layer_sun")
        val MOON=booleanPreferencesKey("layer_moon")
        val GALAXY=booleanPreferencesKey("layer_galaxy")
        val THEME=stringPreferencesKey("theme")
        val REDUCED=booleanPreferencesKey("reduce_transparency")
    }
}

/** Main-thread session: restore once, then serialize complete user choices in order. */
class SettingsSession(scope:CoroutineScope,read:suspend()->UserSettings,write:suspend(UserSettings)->Unit,
    apply:(UserSettings)->Unit,onError:(String)->Unit,onLoaded:()->Unit={}) {
    private var edited=false
    private val writes=Channel<UserSettings>(Channel.UNLIMITED)
    init { scope.launch {
        try { val saved=read();if(!edited)apply(saved) }
        catch(e:CancellationException) {throw e}
        catch(e:Exception) {onError("设置读取失败，原数据已保留：${e.message}")}
        finally {onLoaded()}
        for(value in writes) {
            try {write(value)}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {onError("设置未保存：${e.message}")}
        }
    } }
    fun changed(value:UserSettings) {edited=true;writes.trySend(value)}
}
