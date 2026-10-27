package local.skytrails.app.core

import io.github.cosinekitty.astronomy.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** All solar-system calculations use the pinned Astronomy Engine source. */
class AstronomyEngineAdapter(private val galactic: GalacticDirections = MilkyWayGeometry()): AstronomyService {
    override fun position(target: Target, site: Site, at: Instant, atmosphere: Atmosphere): SkyDirection {
        val time = Time.fromMillisecondsSince1970(at.toEpochMilli())
        val observer = site.observer()
        val equ = equator(target.body(), time, observer, EquatorEpoch.OfDate, Aberration.Corrected)
        val hor = horizon(time, observer, equ.ra, equ.dec,
            if (atmosphere == Atmosphere.AIRLESS) Refraction.None else Refraction.Normal)
        return SkyDirection(hor.azimuth, hor.altitude)
    }

    override fun events(target: Target, site: Site, date: LocalDate): List<SkyEvent> {
        val window = dayWindow(date, site.zone)
        if (window.start == window.endExclusive) return emptyList()
        val result = mutableListOf<SkyEvent>()
        for (kind in EventKind.values()) {
            var cursor = window.start
            while (cursor < window.endExclusive) {
                val remainingDays = Duration.between(cursor, window.endExclusive).toMillis() / 86_400_000.0
                // searchRiseSet already handles limb radius, parallax, refraction and horizon dip.
                val found = searchRiseSet(target.body(), site.observer(),
                    if (kind == EventKind.RISE) Direction.Rise else Direction.Set,
                    Time.fromMillisecondsSince1970(cursor.toEpochMilli()), remainingDays, site.eyeHeightMeters)
                    ?: break
                val at = Instant.ofEpochMilli(found.toMillisecondsSince1970())
                if (at >= window.endExclusive) break
                if (at >= window.start) result.add(SkyEvent(kind, at, position(target, site, at, Atmosphere.STANDARD)))
                // Library roots are solved to 0.1 s; move beyond the same root before continuing.
                cursor = maxOf(at.plusSeconds(1), cursor.plusSeconds(1))
            }
        }
        return result.sortedBy { it.instant }
    }

    override fun galacticPlane(site: Site, at: Instant): List<SkyDirection> =
        galactic.plane(site, at)
    override fun galacticCenter(site: Site, at: Instant): SkyDirection =
        galactic.center(site, at)

    private fun Target.body() = if (this == Target.SUN) Body.Sun else Body.Moon
    private fun Site.observer() = Observer(latitude, longitude, elevationMeters + eyeHeightMeters)
}
