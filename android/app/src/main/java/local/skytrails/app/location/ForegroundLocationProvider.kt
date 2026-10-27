package local.skytrails.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.time.Instant
import java.time.ZoneId

/**
 * Foreground-only LocationManager adapter; the Activity owns permission prompts.
 * Bind to its lifecycle and call refresh only while STARTED. Stop before manually selecting a place.
 * Last known fixes retain their timestamp/accuracy, so UI can show their age rather than call them fresh.
 */
class ForegroundLocationProvider(context: Context, private val repository: LocationRepository): DefaultLifecycleObserver {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private var listener: LocationListener? = null
    private var timeout: Runnable? = null
    private var foreground = false
    private var generation = 0

    override fun onStart(owner: LifecycleOwner) { foreground = true }
    override fun onStop(owner: LifecycleOwner) { foreground = false; stop() }
    override fun onDestroy(owner: LifecycleOwner) { stop(); owner.lifecycle.removeObserver(this) }

    fun refresh(deviceZone: ZoneId = ZoneId.systemDefault()) {
        stop()
        if (!foreground) { repository.fail("请返回前台后刷新位置"); return }
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) { repository.fail("暂未获得定位权限，可手动设置", true); return }
        val repositoryRequest = repository.beginDeviceRefresh()
        val request = generation
        try {
            val providers = (if (fine) listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                else listOf(LocationManager.NETWORK_PROVIDER)).filter { it in manager.allProviders && manager.isProviderEnabled(it) }
            if (providers.isEmpty()) { repository.fail("系统定位不可用，可手动设置"); return }
            providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let { accept(it, deviceZone, repositoryRequest) }
            val callback = object: LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!foreground || request != generation) return
                    accept(location, deviceZone, repositoryRequest)
                    stop()
                }
                override fun onProviderDisabled(provider: String) {
                    if (request == generation && providers.none { manager.isProviderEnabled(it) }) {
                        repository.fail("系统定位已关闭，可手动设置"); stop()
                    }
                }
            }
            listener = callback
            for (provider in providers) manager.requestLocationUpdates(provider, 1000L, 0f, callback, Looper.getMainLooper())
            timeout = Runnable {
                if (request == generation) { repository.fail("暂未获得新位置，可使用已显示位置或手动设置"); stop() }
            }.also { handler.postDelayed(it, 20_000L) }
        } catch (_: SecurityException) {
            repository.fail("定位权限已撤销，可手动设置", true); stop()
        } catch (e: IllegalArgumentException) {
            repository.fail("定位服务不可用，可手动设置"); stop()
        }
    }

    fun stop() {
        generation++
        timeout?.let(handler::removeCallbacks); timeout = null
        listener?.let { runCatching { manager.removeUpdates(it) } }; listener = null
        repository.endDeviceRefresh()
    }

    private fun accept(location: Location, zone: ZoneId, requestId: Long) {
        // Android Location.altitude is WGS84 ellipsoid height; deliberately not used as sea-level elevation.
        repository.acceptDeviceFix(DeviceFix(location.latitude, location.longitude, Instant.ofEpochMilli(location.time),
            if (location.hasAccuracy()) location.accuracy.toDouble() else null, location.provider ?: "unknown"), zone, requestId)
    }
}
