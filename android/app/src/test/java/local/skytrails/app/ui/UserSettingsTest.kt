package local.skytrails.app.ui
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
@OptIn(ExperimentalCoroutinesApi::class)
class UserSettingsTest {
 @get:Rule val folder=TemporaryFolder()
 @Test fun realStoreDefaultsRoundtripsAndReopensWithoutTouchingUnknownKeys()=runBlocking {
  val file=File(folder.root,"settings.preferences_pb")
  var job=SupervisorJob()
  var data=PreferenceDataStoreFactory.create(scope=CoroutineScope(job+Dispatchers.IO),produceFile={file})
  try {
   val store=UserSettingsStore(data)
   assertEquals(UserSettings(),store.read())
   data.edit{it[stringPreferencesKey("unrelated") ]="preserved"}
   for(mode in AppThemeMode.entries) {store.write(UserSettings(mode,true));assertEquals(UserSettings(mode,true),store.read())}
   store.write(UserSettings(AppThemeMode.NIGHT,true))
  } finally {job.cancelAndJoin()}
  job=SupervisorJob();data=PreferenceDataStoreFactory.create(scope=CoroutineScope(job+Dispatchers.IO),produceFile={file})
  try {
   assertEquals(UserSettings(AppThemeMode.NIGHT,true),UserSettingsStore(data).read())
   assertEquals("preserved",data.data.first()[stringPreferencesKey("unrelated")])
   data.edit{it[stringPreferencesKey("theme") ]="FUTURE_THEME"}
   assertEquals(UserSettings(AppThemeMode.SYSTEM,true),UserSettingsStore(data).read())
  } finally {job.cancelAndJoin()}
 }
 @Test fun delayedInitialReadCannotOverwriteNewChoiceAndWritesStayOrdered()=runTest {
  val initial=CompletableDeferred<UserSettings>();val firstWrite=CompletableDeferred<Unit>()
  var shown=UserSettings();val saved=mutableListOf<UserSettings>();val errors=mutableListOf<String>()
  val session=SettingsSession(backgroundScope,{initial.await()},{value->if(saved.isEmpty())firstWrite.await();saved+=value},{shown=it},{errors+=it})
  runCurrent()
  val first=UserSettings(AppThemeMode.NIGHT,false);val last=UserSettings(AppThemeMode.OUTDOOR,true)
  shown=first;session.changed(first);shown=last;session.changed(last)
  initial.complete(UserSettings(AppThemeMode.LIGHT,false));runCurrent()
  assertEquals(last,shown);assertTrue(saved.isEmpty())
  firstWrite.complete(Unit);runCurrent()
  assertEquals(listOf(first,last),saved);assertTrue(errors.isEmpty())
 }
 @Test fun initialSettingsAreAppliedAndWriteFailureIsVisibleWithoutDroppingNextWrite()=runTest {
  var shown=UserSettings();val errors=mutableListOf<String>();val saved=mutableListOf<UserSettings>()
  val initial=UserSettings(AppThemeMode.NIGHT,true)
  val session=SettingsSession(backgroundScope,{initial},{if(it.theme==AppThemeMode.LIGHT)error("disk full") else saved+=it},{shown=it},{errors+=it})
  runCurrent();assertEquals(initial,shown)
  session.changed(UserSettings(AppThemeMode.LIGHT));session.changed(UserSettings(AppThemeMode.DARK));runCurrent()
  assertEquals(1,errors.size);assertEquals(listOf(UserSettings(AppThemeMode.DARK)),saved)
 }
 @Test fun loadingGateWaitsForRestoredSettingsAndOpensAfterReadFailure()=runTest {
  val read=CompletableDeferred<UserSettings>();var ready=false;var shown=UserSettings()
  SettingsSession(backgroundScope,{read.await()},{},{shown=it},{error(it)},{ready=true})
  runCurrent();assertFalse(ready)
  read.complete(UserSettings(AppThemeMode.NIGHT,true));runCurrent()
  assertTrue(ready);assertEquals(AppThemeMode.NIGHT,shown.theme)
  var failedReady=false;val errors=mutableListOf<String>()
  SettingsSession(backgroundScope,{error("read failed")},{},{},{errors+=it},{failedReady=true})
  runCurrent();assertTrue(failedReady);assertEquals(1,errors.size)
 }
 @Test fun explicitDefaultChoiceDuringLoadStillWins()=runTest {
  val read=CompletableDeferred<UserSettings>();var shown=UserSettings();val saved=mutableListOf<UserSettings>()
  val session=SettingsSession(backgroundScope,{read.await()},{saved+=it},{shown=it},{error(it)})
  session.changed(UserSettings());read.complete(UserSettings(AppThemeMode.NIGHT,true));runCurrent()
  assertEquals(UserSettings(),shown);assertEquals(listOf(UserSettings()),saved)
 }

}
