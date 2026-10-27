package local.skytrails.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import local.skytrails.app.location.ForegroundLocationProvider
import local.skytrails.app.ui.*

class MainActivity:ComponentActivity() {
 private val model:SkyViewModel by viewModels()
 private lateinit var locationProvider:ForegroundLocationProvider
 private var requestingLocation=false
 private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
   updatePermissions()
   if(requestingLocation) {
    requestingLocation=false
    if(!model.state.value.locationPermission) model.startupLocation.denied()
   }
   refreshPendingLocation()
 }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState);enableEdgeToEdge()
  locationProvider=ForegroundLocationProvider(this,model.locations);lifecycle.addObserver(locationProvider)
  updatePermissions()
  setContent { SkyApp(model,requestCamera={permissions.launch(arrayOf(Manifest.permission.CAMERA))},requestLocation={
   model.startupLocation.requestExplicit();requestingLocation=true;permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
  },stopLocation={locationProvider.stop()}) }
 }
 override fun onResume() { super.onResume();updatePermissions();refreshPendingLocation() }
 private fun refreshPendingLocation() {
  val s=model.state.value
  if(model.startupLocation.take(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED),
      s.locationPermission,model.locations.state.value.selected)) locationProvider.refresh()
 }
 private fun updatePermissions() {
  fun granted(p:String)=ContextCompat.checkSelfPermission(this,p)==PackageManager.PERMISSION_GRANTED
  model.change { it.copy(cameraPermission=granted(Manifest.permission.CAMERA),locationPermission=granted(Manifest.permission.ACCESS_FINE_LOCATION)||granted(Manifest.permission.ACCESS_COARSE_LOCATION)) }
 }
}
