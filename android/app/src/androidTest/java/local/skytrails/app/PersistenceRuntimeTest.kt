package local.skytrails.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import local.skytrails.app.location.*
import local.skytrails.app.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Uses real DataStore/native IO but isolated test files; never changes user favorites or settings. */
@RunWith(AndroidJUnit4::class)
class PersistenceRuntimeTest {
 @Test fun actualAndroidSettingsAndFavoriteStoresSurviveCloseAndReopen()=runBlocking {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val directory=File(context.cacheDir,"persistence-test-${java.util.UUID.randomUUID()}").apply{mkdirs()}
  val settingsFile=File(directory,"settings.preferences_pb")
  val favoritesFile=File(directory,"favorites.preferences_pb")
  val favorite=Favorite("runtime-fixture",ManualLocationInput("Synthetic test","30","120","Asia/Shanghai").selection())
  var job=SupervisorJob()
  try {
   var scope=CoroutineScope(job+Dispatchers.IO)
   val settings=UserSettingsStore(PreferenceDataStoreFactory.create(scope=scope,produceFile={settingsFile}))
   val favorites=FavoriteStore(PreferenceDataStoreFactory.create(scope=scope,produceFile={favoritesFile}))
   settings.write(UserSettings(AppThemeMode.NIGHT,true,SkyLayers(false,true,false)));favorites.save(favorite)
   assertEquals(UserSettings(AppThemeMode.NIGHT,true,SkyLayers(false,true,false)),settings.read())
   assertEquals(listOf(favorite),favorites.favorites.first())
   job.cancelAndJoin()
   job=SupervisorJob();scope=CoroutineScope(job+Dispatchers.IO)
   val reopenedSettings=UserSettingsStore(PreferenceDataStoreFactory.create(scope=scope,produceFile={settingsFile}))
   val reopenedFavorites=FavoriteStore(PreferenceDataStoreFactory.create(scope=scope,produceFile={favoritesFile}))
   assertEquals(UserSettings(AppThemeMode.NIGHT,true,SkyLayers(false,true,false)),reopenedSettings.read())
   assertEquals(listOf(favorite),reopenedFavorites.favorites.first())
   assertFalse(reopenedFavorites.favorites.first().single().location.confirmedManualOnSite)
  } finally {job.cancelAndJoin();directory.deleteRecursively()}
 }
}
