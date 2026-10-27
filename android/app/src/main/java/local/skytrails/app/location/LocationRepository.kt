package local.skytrails.app.location

import local.skytrails.app.core.Site
import java.time.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LocationSource { DEVICE, MANUAL, CITY_CENTER }
enum class ElevationSource { ASSUMED_ZERO, MANUAL }
enum class ZoneSource { DEVICE, EXPLICIT, CITY }
enum class LocationStatus { UNSET, LOCATING, READY, FAILED, PERMISSION_DENIED }

data class SelectedLocation(val name: String, val site: Site, val source: LocationSource,
    val elevationSource: ElevationSource, val zoneSource: ZoneSource,
    val fixAt: Instant? = null, val accuracyMeters: Double? = null, val provider: String? = null,
    val confirmedManualOnSite: Boolean = false) {
    fun ageAt(now: Instant): Duration? = fixAt?.let { Duration.between(it, now).coerceAtLeast(Duration.ZERO) }
}

data class LocationState(val status: LocationStatus = LocationStatus.UNSET,
    val selected: SelectedLocation? = null, val error: String? = null)

data class ManualLocationInput(val name: String, val latitude: String, val longitude: String,
    val zoneId: String, val elevationMeters: String = "", val eyeHeightMeters: String = "0",
    val confirmedOnSite: Boolean = false) {
    fun selection(): SelectedLocation {
        require(zoneId.isNotBlank()) { "请明确选择地点 IANA 时区" }
        require(zoneId.trim() in ZoneId.getAvailableZoneIds() || zoneId.trim() == "UTC") { "未知 IANA 时区" }
        val site = Site(number(latitude, "纬度"), number(longitude, "经度"),
            if (elevationMeters.isBlank()) 0.0 else number(elevationMeters, "海拔"),
            number(eyeHeightMeters, "离地高度"), ZoneId.of(zoneId.trim()))
        return SelectedLocation(name.trim().ifBlank { "手动位置" }, site, LocationSource.MANUAL,
            if (elevationMeters.isBlank()) ElevationSource.ASSUMED_ZERO else ElevationSource.MANUAL,
            ZoneSource.EXPLICIT, confirmedManualOnSite = confirmedOnSite)
    }
    companion object {
        fun parseCoordinatePair(text: String): Pair<Double, Double> {
            val parts = text.trim().split(Regex("[,，\\s]+"))
            require(parts.size == 2) { "请输入 WGS84 纬度,经度；不接受 GCJ-02/BD-09 坐标" }
            val lat = number(parts[0], "纬度"); val lon = number(parts[1], "经度")
            Site(lat, lon, 0.0, 0.0, ZoneOffset.UTC)
            return lat to lon
        }
        private fun number(text: String, field: String): Double =
            requireNotNull(text.trim().toDoubleOrNull()?.takeIf { it.isFinite() }) { "$field 请输入有限数值" }
    }
}

data class DeviceFix(val latitude: Double, val longitude: Double, val at: Instant,
    val accuracyMeters: Double?, val provider: String)

class LocationRepository {
    private val mutable = MutableStateFlow(LocationState())
    private var deviceRequestId = 0L
    val state: StateFlow<LocationState> = mutable.asStateFlow()
    fun setManual(input: ManualLocationInput): Result<Unit> = runCatching { select(input.selection()) }
        .onFailure { fail(it.message ?: "地点输入无效") }
    fun select(location: SelectedLocation) {
        deviceRequestId++
        mutable.value = LocationState(LocationStatus.READY, location)
    }
    fun beginDeviceRefresh(): Long {
        deviceRequestId++
        mutable.value = mutable.value.copy(status = LocationStatus.LOCATING, error = null)
        return deviceRequestId
    }
    fun endDeviceRefresh() {
        deviceRequestId++
        if (mutable.value.status == LocationStatus.LOCATING) {
            mutable.value = mutable.value.copy(status = if (mutable.value.selected == null) LocationStatus.UNSET else LocationStatus.READY)
        }
    }
    fun acceptDeviceFix(fix: DeviceFix, deviceZone: ZoneId, requestId: Long? = null): Result<Unit> = runCatching {
        if (requestId != null && requestId != deviceRequestId) return@runCatching
        val old = mutable.value.selected
        if (old?.source == LocationSource.DEVICE && old.fixAt != null && fix.at < old.fixAt) return@runCatching
        require(fix.accuracyMeters == null || (fix.accuracyMeters.isFinite() && fix.accuracyMeters >= 0.0)) { "无效定位精度" }
        mutable.value = LocationState(LocationStatus.READY, SelectedLocation("手机位置", Site(fix.latitude, fix.longitude, 0.0, 0.0, deviceZone),
            LocationSource.DEVICE, ElevationSource.ASSUMED_ZERO, ZoneSource.DEVICE,
            fix.at, fix.accuracyMeters, fix.provider))
    }.onFailure { fail(it.message ?: "定位数据无效") }
    fun fail(message: String, permissionDenied: Boolean = false) {
        mutable.value = mutable.value.copy(status = if (permissionDenied) LocationStatus.PERMISSION_DENIED else LocationStatus.FAILED, error = message)
    }
}

sealed interface LocalTimeResolution {
    data class Resolved(val instant: Instant, val offset: ZoneOffset): LocalTimeResolution
    data class Gap(val nextValidLocalTime: LocalDateTime): LocalTimeResolution
    data class Overlap(val offsets: List<ZoneOffset>): LocalTimeResolution
}
fun resolveLocalTime(local: LocalDateTime, zone: ZoneId, chosenOffset: ZoneOffset? = null): LocalTimeResolution {
    val offsets = zone.rules.getValidOffsets(local)
    if (offsets.isEmpty()) return LocalTimeResolution.Gap(zone.rules.getTransition(local).dateTimeAfter)
    if (chosenOffset != null) require(chosenOffset in offsets) { "所选 UTC 偏移不适用于此当地时间" }
    if (offsets.size > 1 && chosenOffset == null) return LocalTimeResolution.Overlap(offsets)
    val offset = chosenOffset ?: offsets.single()
    return LocalTimeResolution.Resolved(local.toInstant(offset), offset)
}
