package local.skytrails.app.core

import java.time.Instant
import java.time.ZoneId

enum class Target { SUN, MOON }
enum class EventKind { RISE, SET }
enum class Atmosphere { AIRLESS, STANDARD }

/**
 * WGS84 ground-observation site. Ground elevation is -500..10000 m; eye height is
 * independently 0..1000 m above that ground (absolute observer height <=11000 m).
 * This app does not support flight/space observation: the pinned rise/set horizon-
 * dip model is not valid over its entire atmospheric-density input range.
 * Out-of-domain input is rejected, never clamped or replaced with another height.
 */
data class Site(val latitude: Double, val longitude: Double,
                val elevationMeters: Double, val eyeHeightMeters: Double,
                val zone: ZoneId) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "纬度须在 -90 至 90 度之间（WGS84）" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "经度须在 -180 至 180 度之间（WGS84）" }
        require(elevationMeters.isFinite() && elevationMeters in -500.0..10000.0) { "地面观测海拔须在 -500 至 10000 米之间" }
        require(eyeHeightMeters.isFinite() && eyeHeightMeters in 0.0..1000.0) { "地面观测离地高度须在 0 至 1000 米之间" }
    }
}
data class SkyDirection(val azimuthDeg: Double, val altitudeDeg: Double)
data class SkyEvent(val kind: EventKind, val instant: Instant, val direction: SkyDirection)
data class DayWindow(val start: Instant, val endExclusive: Instant)
