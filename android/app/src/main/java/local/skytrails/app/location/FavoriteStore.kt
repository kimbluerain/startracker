package local.skytrails.app.location

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import local.skytrails.app.core.Site
import java.time.ZoneId
import java.util.Base64

/** Reuse this singleton delegate; never create multiple stores for the same file. */
val Context.skyTrailsFavoriteDataStore by preferencesDataStore(name = "sky_trails_favorites")

data class Favorite(val id: String, val location: SelectedLocation)

/** One application-owned DataStore instance must be reused per file. No cloud storage. */
class FavoriteStore(private val dataStore: DataStore<Preferences>) {
    val favorites: Flow<List<Favorite>> = dataStore.data.map(::decode)

    suspend fun save(favorite: Favorite) {
        require(favorite.id.isNotBlank()) { "Favorite id is required" }
        dataStore.edit { preferences ->
            val values = decode(preferences).filterNot { it.id == favorite.id } + favorite
            preferences[VERSION] = 1
            preferences[RECORDS] = values.joinToString("\n") { encode(it) }
        }
    }

    suspend fun remove(id: String) {
        dataStore.edit { preferences ->
            val values = decode(preferences).filterNot { it.id == id }
            preferences[VERSION] = 1
            preferences[RECORDS] = values.joinToString("\n") { encode(it) }
        }
    }

    companion object {
        private val VERSION = intPreferencesKey("favorite_schema")
        private val RECORDS = stringPreferencesKey("favorite_records")
        private fun text(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
        private fun untext(value: String) = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
        private fun encode(favorite: Favorite): String {
            val location = favorite.location
            val site = location.site
            // A saved coordinate is not a fresh GPS fix or a renewed on-site confirmation.
            val source = if (location.source == LocationSource.DEVICE) LocationSource.MANUAL else location.source
            val zoneSource = if (location.zoneSource == ZoneSource.DEVICE) ZoneSource.EXPLICIT else location.zoneSource
            return listOf(text(favorite.id),text(location.name),site.latitude.toString(),site.longitude.toString(),
                site.elevationMeters.toString(),site.eyeHeightMeters.toString(),text(site.zone.id),source.name,
                location.elevationSource.name,zoneSource.name).joinToString("\t")
        }
        private fun decode(preferences: Preferences): List<Favorite> {
            val version = preferences[VERSION]
            val records = preferences[RECORDS]
            if (version == null && records == null) return emptyList()
            require(version == 1) { "Unsupported favorites schema; existing data was preserved" }
            requireNotNull(records) { "Missing favorite records; existing data was preserved" }
            return records.lineSequence().filter { it.isNotEmpty() }.map { line ->
                val c = line.split('\t')
                require(c.size == 10) { "Invalid favorite record; existing data was preserved" }
                val location = SelectedLocation(untext(c[1]), Site(c[2].toDouble(),c[3].toDouble(),c[4].toDouble(),
                    c[5].toDouble(),ZoneId.of(untext(c[6]))),LocationSource.valueOf(c[7]),ElevationSource.valueOf(c[8]),ZoneSource.valueOf(c[9]))
                require(location.source != LocationSource.DEVICE && location.zoneSource != ZoneSource.DEVICE) { "Favorite cannot be a current device fix" }
                Favorite(untext(c[0]),location)
            }.toList().also { values ->
                require(values.all { it.id.isNotBlank() } && values.map { it.id }.distinct().size == values.size) { "Invalid or duplicate favorite id" }
            }
        }
    }
}
