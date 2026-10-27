package local.skytrails.app.ui

import android.graphics.Bitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Convert imagery before either display or blur; a translucent red overlay preserves blue. */
fun nightBackdropPixel(argb:Int):Int {
 val luminance=(((argb ushr 16)and 255)*54+((argb ushr 8)and 255)*183+(argb and 255)*19)/256
 val red=(luminance*.60).toInt();val green=(red*.12).toInt();val blue=(red*.08).toInt()
 return (argb and 0xff000000.toInt())or(red shl 16)or(green shl 8)or blue
}
suspend fun nightBackdropBitmap(source:Bitmap):Bitmap {
 val pixels=IntArray(source.width*source.height)
 source.getPixels(pixels,0,source.width,0,0,source.width,source.height)
 for(i in pixels.indices){if(i%16384==0)currentCoroutineContext().ensureActive();pixels[i]=nightBackdropPixel(pixels[i])}
 return Bitmap.createBitmap(pixels,source.width,source.height,Bitmap.Config.ARGB_8888)
}
