package local.skytrails.app.ui

import local.skytrails.app.location.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

internal data class DateTimeInput(val local:LocalDateTime,val zone:ZoneId)
/** A DST confirmation belongs to one exact visible input, never a previous edit. */
internal class PendingDateTime(val input:DateTimeInput) {
 val resolution=resolveLocalTime(input.local,input.zone)
 fun choose(current:DateTimeInput,offset:ZoneOffset?=null):Instant? {
  if(current!=input)return null
  return when(val r=resolution) {
   is LocalTimeResolution.Resolved->r.instant
   is LocalTimeResolution.Gap->r.nextValidLocalTime.atZone(input.zone).toInstant()
   is LocalTimeResolution.Overlap->offset?.takeIf{it in r.offsets}?.let(input.local::toInstant)
  }
 }
}

private val manualDateTimeFormatter=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm",Locale.ROOT)
 .withResolverStyle(ResolverStyle.STRICT)

internal fun parseManualDateTime(value:String):LocalDateTime {
 val local=try { LocalDateTime.parse(value.trim(),manualDateTimeFormatter) }
 catch(e:DateTimeParseException) { throw IllegalArgumentException("日期或时间无效，请按 yyyy-MM-dd HH:mm 输入真实日期（小时 00—23，分钟 00—59）",e) }
 require(local.year in 2000..2050){"日期须在 2000—2050 年"}
 return local
}

/** A location change preserves Instant even when its new local date falls outside the UI range. */
internal fun timelinePreviewInstant(at:Instant,zone:ZoneId,fraction:Double):Instant? {
 val date=at.atZone(zone).toLocalDate()
 if(date.year !in 2000..2050)return null
 return instantOnDay(date,zone,fraction)
}
