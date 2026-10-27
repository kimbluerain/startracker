package local.skytrails.app.ui

import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class SkyLayersTest {
 @get:Rule val folder=TemporaryFolder()
 @Test fun allEightCombinationsToggleOnlyRequestedLayerAndKeepPlanningFocusSeparate() {
  for(bits in 0..7) {
   val layers=SkyLayers(bits and 1!=0,bits and 2!=0,bits and 4!=0)
   val state=AppState(target=SkyTarget.MOON,layers=layers)
   for(target in SkyTarget.entries) {
    val next=state.copy(layers=layers.toggle(target))
    assertEquals(SkyTarget.MOON,next.target)
    SkyTarget.entries.forEach {t->assertEquals(if(t==target)!layers.contains(t) else layers.contains(t),next.layers.contains(t))}
    assertEquals(layers,next.layers.toggle(target))
   }
  }
  assertTrue(SkyLayers(false,false,false).visibleTargets.isEmpty())
 }
 @Test fun eventEnablesItsOwnLayerWithoutChangingOtherChoicesOrHidingControls() {
  val instant=Instant.parse("2026-10-24T01:23:45Z")
  for(bits in 0..7)for(target in SkyTarget.entries) {
   val before=AppState(layers=SkyLayers(bits and 1!=0,bits and 2!=0,bits and 4!=0),controlsHidden=true)
   val next=before.event(target,instant)
   assertEquals(target,next.target);assertEquals(instant,next.instant);assertFalse(next.live)
   assertTrue(next.controlsHidden)
   SkyTarget.entries.forEach{t->assertEquals(if(t==target)true else before.layers.contains(t),next.layers.contains(t))}
  }
 }
 @Test fun layerChoicesPersistAcrossStoreRecreationAndOldSettingsMigrateWithoutLosingUnknownKeys()=runBlocking {
  val file=File(folder.root,"layers.preferences_pb");var job=SupervisorJob()
  var data=PreferenceDataStoreFactory.create(scope=CoroutineScope(job+Dispatchers.IO),produceFile={file})
  try {
   data.edit{it[stringPreferencesKey("theme")]="NIGHT";it[booleanPreferencesKey("reduce_transparency")]=true;it[stringPreferencesKey("future_key")]="keep"}
   val store=UserSettingsStore(data)
   assertEquals(UserSettings(AppThemeMode.NIGHT,true,SkyLayers()),store.read())
   for(bits in 0..7) {
    val settings=UserSettings(AppThemeMode.NIGHT,true,SkyLayers(bits and 1!=0,bits and 2!=0,bits and 4!=0))
    store.write(settings);assertEquals(settings,store.read())
   }
   store.write(UserSettings(AppThemeMode.NIGHT,true,SkyLayers(false,false,false)))
  }finally{job.cancelAndJoin()}
  job=SupervisorJob();data=PreferenceDataStoreFactory.create(scope=CoroutineScope(job+Dispatchers.IO),produceFile={file})
  try {assertEquals(UserSettings(AppThemeMode.NIGHT,true,SkyLayers(false,false,false)),UserSettingsStore(data).read());assertEquals("keep",data.data.first()[stringPreferencesKey("future_key")])}finally{job.cancelAndJoin()}
 }
 @Test fun enablingFocusesTheLayerAndDisablingFocusFallsBackOnlyToVisibleLayers() {
  for(bits in 0..7)for(focus in SkyTarget.entries)for(layer in SkyTarget.entries) {
   val before=AppState(target=focus,layers=SkyLayers(bits and 1!=0,bits and 2!=0,bits and 4!=0))
   val after=before.toggleLayer(layer)
   assertEquals(before.layers.toggle(layer),after.layers)
   if(after.layers.contains(layer))assertEquals(layer,after.target)
   else if(focus==layer)assertEquals(after.layers.visibleTargets.firstOrNull() ?: focus,after.target)
   else assertEquals(focus,after.target)
  }
 }

 @Test fun displaySwitchDoesNotChangePlanningOrSkyFocus() {
  val before=AppState(target=SkyTarget.MOON,starFocusId="orion",skyFollow=true,focusRequest=7)
  for(layer in DisplayLayer.entries) {
   val after=before.toggleDisplayLayer(layer)
   assertEquals(before.target,after.target)
   assertEquals(before.starFocusId,after.starFocusId)
   assertEquals(before.skyFollow,after.skyFollow)
   assertEquals(before.focusRequest,after.focusRequest)
   DisplayLayer.entries.forEach { other->assertEquals(if(other==layer)!before.displays(other) else before.displays(other),after.displays(other)) }
  }
 }

}
