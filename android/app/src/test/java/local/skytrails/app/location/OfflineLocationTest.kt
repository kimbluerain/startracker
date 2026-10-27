package local.skytrails.app.location

import androidx.datastore.preferences.core.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class OfflineLocationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun offlineSearchFindsVerifiedChineseAliasesAndNeverGuessesUnknownPlaces() {
        val index=CityIndex.readGzip(File("src/main/assets/cities15000.data").inputStream())
        assertEquals(34179,index.cities.size)
        val hangzhou=index.search("杭州").first()
        assertEquals("1808926",hangzhou.id)
        assertEquals(30.29365,hangzhou.latitude,0.000001)
        assertEquals("Asia/Shanghai",hangzhou.zoneId)
        assertEquals("1814870",index.search("义乌").first().id)
        assertEquals(hangzhou.id,index.search("hangzhou").first().id)
        assertEquals("1816670",index.search("北京").first().id)
        assertTrue(index.search("ThisCityDefinitelyDoesNotExist_387652").isEmpty())
        assertTrue(index.search(" ").isEmpty())
        assertEquals(LocationSource.CITY_CENTER,hangzhou.selection().source)
        assertEquals(ElevationSource.ASSUMED_ZERO,hangzhou.selection().elevationSource)
    }


    @Test fun previousSchemaOneReadsAndFutureSchemaIsPreservedOnAttemptedWrite() = runBlocking {
        val file=File(folder.root,"versioned.preferences_pb")
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val data=PreferenceDataStoreFactory.create(scope=scope,produceFile={file})
        try {
            val record=javaClass.getResourceAsStream("/location/favorites-v1.txt")!!.reader().readText().trimEnd()
            data.edit { it[intPreferencesKey("favorite_schema")]=1;it[stringPreferencesKey("favorite_records")]=record }
            val store=FavoriteStore(data)
            val read=store.favorites.first().single()
            assertEquals("saved-v1",read.id)
            assertEquals("旧版屋顶",read.location.name)
            assertEquals(108.0,read.location.site.elevationMeters,0.0)
            assertEquals(2.0,read.location.site.eyeHeightMeters,0.0)
            assertEquals("Asia/Shanghai",read.location.site.zone.id)
            data.edit { it[intPreferencesKey("favorite_schema")]=99 }
            assertTrue(runCatching { store.save(read) }.isFailure)
            assertEquals(99,data.data.first()[intPreferencesKey("favorite_schema")])
            assertEquals(record,data.data.first()[stringPreferencesKey("favorite_records")])
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun favoriteDoesNotRestoreStaleGpsOrOnSiteConfirmationAsCurrent() = runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val data=PreferenceDataStoreFactory.create(scope=scope,produceFile={File(folder.root,"snapshot.preferences_pb")})
        try {
            val repo=LocationRepository()
            repo.acceptDeviceFix(DeviceFix(30.0,120.0,Instant.parse("2026-10-23T00:00:00Z"),3.0,"gps"),ZoneId.of("Asia/Shanghai")).getOrThrow()
            val store=FavoriteStore(data)
            store.save(Favorite("snapshot",repo.state.value.selected!!.copy(confirmedManualOnSite=true)))
            val saved=store.favorites.first().single().location
            assertEquals(LocationSource.MANUAL,saved.source)
            assertEquals(ZoneSource.EXPLICIT,saved.zoneSource)
            assertNull(saved.fixAt)
            assertNull(saved.accuracyMeters)
            assertFalse(saved.confirmedManualOnSite)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun favoritesSurviveDataStoreCloseAndReopenAndCanBeUpdatedAndRemoved() = runBlocking {
        val file=File(folder.root,"favorites.preferences_pb")
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val store=FavoriteStore(PreferenceDataStoreFactory.create(scope=scope,produceFile={file}))
        assertTrue(store.favorites.first().isEmpty())
        val original=Favorite("place-1",ManualLocationInput("家\t屋顶\n一号","30.27","120.15","Asia/Shanghai","108","2",false).selection())
        store.save(original)
        assertEquals(listOf(original),store.favorites.first())
        val changed=original.copy(location=original.location.copy(name="改名"))
        store.save(changed)
        assertEquals(listOf(changed),store.favorites.first())
        scope.coroutineContext[Job]!!.cancelAndJoin()
        val newScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            val reopened=FavoriteStore(PreferenceDataStoreFactory.create(scope=newScope,produceFile={file}))
            assertEquals(listOf(changed),reopened.favorites.first())
            reopened.remove("place-1")
            assertTrue(reopened.favorites.first().isEmpty())
        } finally { newScope.coroutineContext[Job]!!.cancelAndJoin() }
    }
}
