package local.skytrails.app.ui.renderer

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** One decoder, at most two decoded bitmaps awaiting GL, and a replaceable bounded work list. */
class GalaxyTileLoader(private val assets:AssetManager,private val ready:()->Unit) {
 private val lock=Any()
 private var active=true;private var generation=0L;private var running=false;private var inFlight=0
 private var executor:ExecutorService?=null
 private var wanted=emptyList<GalaxyTile>()
 private val results=linkedMapOf<GalaxyTile,Bitmap>()
 private val failed=mutableSetOf<GalaxyTile>()
 @Volatile var decodeCount=0L;private set
 @Volatile var lastError:String?=null;private set
 val pendingJobs get()=synchronized(lock){wanted.count{it !in failed}}
 val idle get()=synchronized(lock){!running&&inFlight==0&&results.isEmpty()&&wanted.all{it in failed}}
 fun request(tiles:List<GalaxyTile>)=synchronized(lock) {
  require(tiles.size<=GalaxyTiles.MAX_RESIDENT)
  if(!active)return@synchronized
  wanted=tiles
  results.keys.filter{it !in tiles}.forEach{results.remove(it)?.recycle()}
  startLocked()
 }
 fun takeReady():List<Pair<GalaxyTile,Bitmap>> = synchronized(lock){results.toList().also{ready->wanted=wanted.filter{key->ready.none{it.first==key}};results.clear()}}
 fun resume()=synchronized(lock){active=true}
 fun pause()=synchronized(lock) {
  active=false;generation++;wanted=emptyList();results.values.forEach{it.recycle()};results.clear();failed.clear()
  executor?.shutdownNow();executor=null;running=false
 }
 private fun startLocked() {
  if(running||inFlight>0||!active||results.size>=2||wanted.all{it in results||it in failed})return
  val token=generation;running=true
  val pool=executor?:Executors.newSingleThreadExecutor{job->Thread(job,"galaxy-tile-decode").apply{isDaemon=true}}.also{executor=it}
  pool.execute {
   try {
    while(true) {
     val key=synchronized(lock){if(!active||token!=generation||results.size>=2)null else wanted.firstOrNull{it !in results&&it !in failed}?.also{inFlight++}}?:break
     val bitmap=try{assets.open(key.path).use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inScaled=false;inPreferredConfig=Bitmap.Config.ARGB_8888})}.also{if(it==null||it.width!=514||it.height!=514){it?.recycle();error("Invalid galaxy tile ${key.path}")}}}
      catch(e:Exception){synchronized(lock){if(token==generation){failed+=key;lastError=e.message}};null}
     val accepted=synchronized(lock){
      inFlight--
      if(bitmap==null)false else {decodeCount++;if(active&&token==generation&&key in wanted){results[key]=bitmap;true}else{bitmap.recycle();false}}
     }
     if(accepted)ready()
    }
   }finally{synchronized(lock){if(token==generation)running=false;startLocked()}}
  }
 }
}
