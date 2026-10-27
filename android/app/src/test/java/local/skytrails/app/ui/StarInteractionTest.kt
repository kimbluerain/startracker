package local.skytrails.app.ui

import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import local.skytrails.app.core.*
import local.skytrails.app.location.ManualLocationInput
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class StarInteractionTest {
 @get:Rule val folder=TemporaryFolder()
 @Test fun selectedPatternFocusIsSeparateFromSolarSystemTargetAndIncludesSiteTime() {
  val at=Instant.parse("2026-10-26T12:00:00Z")
  val place=ManualLocationInput("fixture","30","120","Asia/Shanghai").selection()
  val s=AppState(location=place,instant=at,target=SkyTarget.MOON,skyFollow=true,starLayers=StarLayers(enabled=emptySet()))
  val result=s.focusConstellation("orion")
  assertEquals("orion",result.starFocusId);assertEquals(SkyTarget.MOON,result.target)
  assertFalse(result.skyFollow);assertTrue(result.virtualSky)
  assertTrue("orion" in result.starLayers.enabled)
  assertEquals(place.site,result.skyFocusRequest?.site);assertEquals(at,result.skyFocusRequest?.at)
  assertEquals(s,s.focusConstellation("bad"))
 }
 @Test fun persistedStarsMasterHidesEvenSelectedConstellationGuidance()=runBlocking {
  val file=File(folder.root,"master.preferences_pb")
  val job=SupervisorJob();val data=PreferenceDataStoreFactory.create(scope=CoroutineScope(job+Dispatchers.IO),produceFile={file})
  try {
   data.edit{it[booleanPreferencesKey("layer_stars")]=false}
   val restored=UserSettingsStore(data).read()
   assertFalse("Stars master must hide selected objects without deleting their choices",restored.starLayers.shows("orion"))
   assertTrue("orion" in restored.starLayers.enabled)
  }finally{job.cancelAndJoin()}
 }
 @Test fun unifiedLayersPreserveDetailPreferencesAndRestoreEveryObject() {
  val s=AppState(starLayers=StarLayers(false,false,true,setOf("orion"),twinkle=true))
  val off=s.allDisplayLayers(false)
  assertTrue(DisplayLayer.entries.none(off::displays))
  assertEquals(setOf("orion"),off.starLayers.enabled)
  assertFalse(off.starLayers.shows("orion"))
  val on=off.allDisplayLayers(true)
  assertTrue(DisplayLayer.entries.all(on::displays));assertEquals(StarCatalog.ids,on.starLayers.enabled)
  assertTrue(off.starLayers.twinkle);assertTrue(on.starLayers.twinkle)
  assertFalse(on.starLayers.stars);assertFalse(on.starLayers.lines);assertTrue(on.starLayers.names)
  val starsOnly=off.toggleDisplayLayer(DisplayLayer.STARS)
  assertFalse(starsOnly.layers.sun);assertFalse(starsOnly.layers.moon);assertFalse(starsOnly.layers.galaxy)
  assertTrue(starsOnly.starLayers.masterVisible);assertEquals(s.target,starsOnly.target)
 }
 @Test fun starPreferencesSurviveRealStoreCloseAndReopenIncludingAllObjectsHidden()=runBlocking {
  val file=File(folder.root,"stars.preferences_pb")
  val choices=listOf(StarLayers(false,true,false,setOf("orion")),StarLayers(true,false,true,emptySet(),false,true))
  for(value in choices) {
   var job=SupervisorJob();var data=PreferenceDataStoreFactory.create(scope=CoroutineScope(job+Dispatchers.IO),produceFile={file})
   try{UserSettingsStore(data).write(UserSettings(starLayers=value))}finally{job.cancelAndJoin()}
   job=SupervisorJob();data=PreferenceDataStoreFactory.create(scope=CoroutineScope(job+Dispatchers.IO),produceFile={file})
   try{assertEquals(value,UserSettingsStore(data).read().starLayers)}finally{job.cancelAndJoin()}
  }
 }
}
