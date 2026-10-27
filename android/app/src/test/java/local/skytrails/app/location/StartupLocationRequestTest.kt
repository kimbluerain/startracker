package local.skytrails.app.location
import org.junit.Assert.*
import org.junit.Test
class StartupLocationRequestTest {
 @Test fun alreadyGrantedStartsOnceOnlyInForeground() {
  val request=StartupLocationRequest()
  assertFalse(request.take(false,true,null))
  assertTrue(request.take(true,true,null))
  assertFalse(request.take(true,true,null))
 }
 @Test fun manualChoiceIsNeverStolenButExplicitRefreshWaitsForForeground() {
  val place=ManualLocationInput("manual","30","120","Asia/Shanghai").selection()
  val request=StartupLocationRequest()
  assertFalse(request.take(true,true,place))
  request.requestExplicit()
  assertFalse(request.take(false,true,place))
  assertTrue(request.take(true,true,place))
  assertFalse(request.take(true,true,place))
 }
 @Test fun deniedDoesNotRefreshAndLaterGrantCanUseAutomaticPath() {
  val request=StartupLocationRequest()
  request.requestExplicit();request.denied()
  assertFalse(request.take(true,false,null))
  assertTrue(request.take(true,true,null))
 }
}
