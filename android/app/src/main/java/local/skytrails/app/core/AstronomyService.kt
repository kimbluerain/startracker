package local.skytrails.app.core

import java.time.Instant
import java.time.LocalDate

interface AstronomyService {
    fun position(target: Target, site: Site, at: Instant, atmosphere: Atmosphere): SkyDirection
    fun events(target: Target, site: Site, date: LocalDate): List<SkyEvent>
    fun galacticPlane(site: Site, at: Instant): List<SkyDirection>
    fun galacticCenter(site: Site, at: Instant): SkyDirection
}

/** Implemented by the Galactic coordinate module, independently from solar-system events. */
interface GalacticDirections {
    fun plane(site: Site, at: Instant): List<SkyDirection>
    fun center(site: Site, at: Instant): SkyDirection
}
