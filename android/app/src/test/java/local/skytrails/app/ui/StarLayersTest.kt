package local.skytrails.app.ui
import org.junit.Assert.*
import org.junit.Test
class StarLayersTest {
 @Test fun enabledObjectWithAllRepresentationsOffIsNotVisible() {
  assertFalse(StarLayers(false,false,false,setOf("orion")).shows("orion"))
  assertTrue(StarLayers(false,true,false,setOf("orion")).shows("orion"))
  assertFalse(StarLayers(true,true,true,emptySet()).shows("orion"))
 }
 @Test fun independentObjectToggleDoesNotChangeOtherVisibilityChoices() {
  val initial=StarLayers(stars=false,lines=true,names=false)
  val hidden=initial.toggle("orion")
  assertFalse("orion" in hidden.enabled)
  assertEquals(setOf("dipper","cassiopeia","crux"),hidden.enabled)
  assertFalse(hidden.stars);assertTrue(hidden.lines);assertFalse(hidden.names)
  assertEquals(initial,hidden.toggle("orion"))
  assertEquals(initial,initial.toggle("unknown"))
 }
}
