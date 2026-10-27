package local.skytrails.app.location

import local.skytrails.app.core.Site
import java.io.InputStream
import java.text.Normalizer
import java.time.ZoneId
import java.util.Locale
import java.util.zip.GZIPInputStream

data class CityEntry(val id: String, val name: String, val aliases: List<String>, val country: String,
    val latitude: Double, val longitude: Double, val zoneId: String, val population: Long) {
    fun selection() = SelectedLocation(name, Site(latitude, longitude, 0.0, 0.0, ZoneId.of(zoneId)),
        LocationSource.CITY_CENTER, ElevationSource.ASSUMED_ZERO, ZoneSource.CITY)
}

/** Load on Dispatchers.IO once. Missing names return an empty list; manual WGS84 input stays available. */
class CityIndex private constructor(val cities: List<CityEntry>) {
    private val searchable = cities.map { city -> city to city.aliases.map(::normalize).distinct() }
    fun search(query: String, limit: Int = 20): List<CityEntry> {
        require(limit in 1..100) { "Search limit must be 1..100" }
        val needle = normalize(query.trim())
        if (needle.isBlank()) return emptyList()
        return searchable.mapNotNull { (city, names) ->
            val score = names.minOf { when { it == needle -> 0; it.startsWith(needle) -> 1; it.contains(needle) -> 2; else -> 3 } }
            if (score == 3) null else city to score
        }.sortedWith(compareBy<Pair<CityEntry, Int>> { it.second }.thenByDescending { it.first.population })
            .take(limit).map { it.first }
    }
    companion object {
        private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        fun readGzip(input: InputStream): CityIndex = GZIPInputStream(input).bufferedReader().use { reader ->
            CityIndex(reader.lineSequence().filter { it.isNotBlank() }.map { line ->
                val c=line.split('\t')
                require(c.size == 8) { "Invalid offline city index row" }
                CityEntry(c[0],c[1],c[2].split(','),c[3],c[4].toDouble(),c[5].toDouble(),c[6],c[7].toLong())
            }.toList())
        }
    }
}
