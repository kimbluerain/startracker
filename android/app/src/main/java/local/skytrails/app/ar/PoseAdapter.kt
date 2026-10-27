package local.skytrails.app.ar
import android.content.Context
import android.hardware.*
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/** Sensor input is physical and uses real time; calculation preview time is never accepted. */
class PoseAdapter(context:Context):SensorEventListener {
 private val sensors=context.getSystemService(SensorManager::class.java)
 private val sensor=sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
 private var site:PhysicalSite?=null
 private var lastDeclination:Triple<PhysicalSite,Long,Double>?=null
 val supported get()=sensor!=null
 var running=false;private set
 val frame=AtomicReference<PoseFrame?>(null)
 var quality="方向传感器尚未就绪";private set
 var accuracy=SensorManager.SENSOR_STATUS_UNRELIABLE;private set
 fun start(physical:PhysicalSite) {if(running&&site==physical)return;site=physical;if(sensor==null){quality="缺少旋转矢量传感器，使用虚拟天空";return};running=sensors.registerListener(this,sensor,SensorManager.SENSOR_DELAY_GAME)}
 fun stop(){running=false;sensors.unregisterListener(this);frame.set(null);site=null;quality="方向传感器已暂停"}
 override fun onAccuracyChanged(sensor:Sensor?,value:Int){accuracy=value;quality=when(value){SensorManager.SENSOR_STATUS_ACCURACY_HIGH->"系统方向状态：高（非角度精度）";SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM->"系统方向状态：中";else->"方向可能受磁干扰，请远离金属"}}
 override fun onSensorChanged(event:SensorEvent) {
  val p=site ?: return;val r=FloatArray(9);SensorManager.getRotationMatrixFromVector(r,event.values)
  val now=Instant.now();val minute=now.epochSecond/60
  val cached=lastDeclination
  val d=if(cached?.first==p&&cached.second==minute)cached.third else GeomagneticField(p.latitude.toFloat(),p.longitude.toFloat(),p.altitudeMeters.toFloat(),now.toEpochMilli()).declination.toDouble().also{lastDeclination=Triple(p,minute,it)}
  frame.set(PoseFrame.capture(p,now,DoubleArray(9){r[it].toDouble()},DeclinationProvider {_,_->d}))
  if(quality=="方向传感器尚未就绪"||quality=="方向传感器已暂停")quality="方向传感器可用，待现场校准"
 }
}
