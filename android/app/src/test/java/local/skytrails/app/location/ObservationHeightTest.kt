package local.skytrails.app.location

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import local.skytrails.app.core.*
import local.skytrails.app.core.Target
import java.io.File
import java.time.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Protect the ground-observation domain before inputs reach the library's horizon-dip model. */
class ObservationHeightTest {
    @get:Rule val folder = TemporaryFolder()
    private val valid = ManualLocationInput("原位置", "30.2741", "120.1551", "Asia/Shanghai", "108", "2")

    @Test fun ground44000IsRejectedBeforeReplacingLocationOrFavorite() = rejectHeight(valid.copy(elevationMeters = "44000"))
    @Test fun ground100000IsRejectedBeforeReplacingLocationOrFavorite() = rejectHeight(valid.copy(elevationMeters = "100000", eyeHeightMeters = "0"))
    @Test fun groundJustAbove10000IsRejectedBeforeReplacingLocationOrFavorite() = rejectHeight(valid.copy(elevationMeters = "10000.001"))
    @Test fun groundBelowNegative500IsRejectedBeforeReplacingLocationOrFavorite() = rejectHeight(valid.copy(elevationMeters = "-500.001"))
    @Test fun eye1001IsRejectedBeforeReplacingLocationOrFavorite() = rejectHeight(valid.copy(eyeHeightMeters = "1001"))
    @Test fun negativeEyeHeightIsRejectedBeforeReplacingLocationOrFavorite() = rejectHeight(valid.copy(eyeHeightMeters = "-0.001"))

    private fun rejectHeight(input: ManualLocationInput) = runBlocking {
        val repository = LocationRepository()
        repository.setManual(valid).getOrThrow()
        val original = repository.state.value.selected!!
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = FavoriteStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(folder.root, "height.preferences_pb") }))
        try {
            val favorite = Favorite("retained-place", original)
            store.save(favorite)
            val result = repository.setManual(input)
            assertTrue("Out-of-domain height must be rejected before calculation: $input", result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(original, repository.state.value.selected)
            assertNotNull(repository.state.value.error)
            val save = runCatching { store.save(Favorite(favorite.id, input.selection())) }
            assertTrue("Invalid height must not overwrite the saved place", save.exceptionOrNull() is IllegalArgumentException)
            assertEquals(listOf(favorite), store.favorites.first())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun supportedUpperGroundAndEyeBoundariesCalculateFiniteDirectionsAndEvents() = checkBoundary(10000.0)
    @Test fun supportedNegativeGroundBoundaryCalculatesFiniteDirectionsAndEvents() = checkBoundary(-500.0)

    private fun checkBoundary(ground: Double) {
        val engine = AstronomyEngineAdapter()
        val date = LocalDate.parse("2026-10-23")
        val at = Instant.parse("2026-10-23T00:00:00Z")
        for (eye in listOf(0.0, 1000.0)) {
            val site = Site(30.2741, 120.1551, ground, eye, ZoneId.of("Asia/Shanghai"))
            for (target in Target.values()) {
                for (atmosphere in Atmosphere.values()) assertFinite(engine.position(target, site, at, atmosphere))
                val events = engine.events(target, site, date)
                assertEquals("$target ground=$ground eye=$eye must retain both events", 2, events.size)
                assertEquals(setOf(EventKind.RISE, EventKind.SET), events.map { it.kind }.toSet())
                events.forEach {
                    assertEquals(date, it.instant.atZone(site.zone).toLocalDate())
                    assertFinite(it.direction)
                }
            }
        }
    }

    private fun assertFinite(direction: SkyDirection) {
        assertTrue(direction.azimuthDeg.isFinite() && direction.azimuthDeg >= 0.0 && direction.azimuthDeg < 360.0)
        assertTrue(direction.altitudeDeg.isFinite() && direction.altitudeDeg in -90.0..90.0)
    }
}
