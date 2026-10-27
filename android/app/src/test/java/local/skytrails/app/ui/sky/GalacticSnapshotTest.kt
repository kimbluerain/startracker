package local.skytrails.app.ui.sky

import com.google.gson.JsonParser
import java.io.File
import java.time.*
import java.util.Locale
import kotlin.math.hypot
import local.skytrails.app.ar.Pixel
import local.skytrails.app.core.*
import org.junit.Assert.*
import org.junit.Test

/** Actual production geometry rendered offline; Android UI host is deliberately Task5. */
class GalacticSnapshotTest {
    @Test fun fixedDateVirtualSkyMatchesIndependentAnchorsAndReviewedSvg() {
        val reference=JsonParser.parseReader(javaClass.getResourceAsStream("/reference/galactic/virtual-sky-reference.json")!!.reader()).asJsonObject
        val meta=reference.getAsJsonObject("metadata")
        val s=meta.getAsJsonObject("site"); val v=meta.getAsJsonObject("view")
        val site=Site(s["latitude"].asDouble,s["longitude"].asDouble,0.0,0.0,ZoneId.of(s["zone"].asString))
        val at=Instant.parse(meta["instantUtc"].asString)
        val sky=MilkyWayGeometry().sky(site,at)
        val projection=SkyProjection.virtual(v["width"].asInt,v["height"].asInt,v["horizontalFovDeg"].asDouble,v["azimuthDeg"].asDouble,v["altitudeDeg"].asDouble)
        val drawing=GalacticOverlay.render(sky,projection)
        val tolerance=meta["tolerancePixels"].asDouble
        var maximum=0.0
        // Independent Astropy geometry + independently projected ENU anchors; not a self oracle.
        for(element in reference.getAsJsonArray("anchors")) {
            val anchor=element.asJsonObject
            val role=if(anchor["line"].asString=="plane") GalacticLine.PLANE else GalacticLine.BAND_EDGE
            val want=Pixel(anchor["x"].asDouble,anchor["y"].asDouble)
            val endpoints=drawing.segments.filter {it.line==role && it.belowHorizon==anchor["belowHorizon"].asBoolean}.flatMap {listOf(it.start,it.end)}
            assertFalse("Rendered sky must contain ${anchor["line"]}",endpoints.isEmpty())
            val error=endpoints.minOf {hypot(it.x-want.x,it.y-want.y)}
            assertTrue("Independent visible anchor $anchor drift=$error px",error<tolerance)
            maximum=maxOf(maximum,error)
        }
        assertTrue(drawing.segments.any {it.belowHorizon})
        assertTrue(drawing.segments.any {!it.belowHorizon})
        val svg=svg(drawing)
        val output=File("build/outputs/galactic-snapshot.svg")
        output.parentFile!!.mkdirs();output.writeText(svg)
        val baseline=File("src/test/resources/reference/galactic/virtual-sky-reviewed.svg")
        if(System.getenv("SKYTRAILS_RECORD_SNAPSHOT")=="1") baseline.writeText(svg)
        else assertEquals("Reviewed fixed-date SVG regression",baseline.readText(),svg)
        println("Independent render anchors=${reference.getAsJsonArray("anchors").size()}, max error=$maximum px; segments=${drawing.segments.size}; snapshot=${output.absolutePath}")
    }

    private fun svg(drawing:GalacticDrawing):String=buildString {
        fun n(value:Double)=String.format(Locale.ROOT,"%.3f",value)
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"800\" height=\"500\" viewBox=\"0 0 800 500\">\n")
        append("<rect width=\"800\" height=\"500\" fill=\"#0B1116\"/>\n")
        append("<rect y=\"250\" width=\"800\" height=\"250\" fill=\"#172129\"/>\n")
        append("<path d=\"M0 250H800\" stroke=\"#596570\" stroke-width=\"1\"/>\n")
        for(segment in drawing.segments) {
            val plane=segment.line==GalacticLine.PLANE
            append("<path d=\"M${n(segment.start.x)} ${n(segment.start.y)}L${n(segment.end.x)} ${n(segment.end.y)}\" fill=\"none\" stroke=\"#8DCBC8\" stroke-width=\"${if(plane)2 else 1}\" opacity=\"${if(plane)1 else 0.55}\"")
            // Every one-degree chord is separate: dash pattern is intentionally short enough to show.
            if(segment.belowHorizon) append(" stroke-dasharray=\"2 3\"")
            append("/>\n")
        }
        val center=drawing.center as TargetIndicator.Visible
        append("<circle cx=\"${n(center.pixel.x)}\" cy=\"${n(center.pixel.y)}\" r=\"5\" fill=\"#F1F0E8\"/>\n")
        append("<g fill=\"#F1F0E8\" font-family=\"sans-serif\" font-size=\"16\">\n")
        append("<text x=\"20\" y=\"28\">Virtual sky · Hangzhou · 2026-10-23 20:00 +08:00</text>\n")
        append("<text x=\"${n(center.pixel.x+10)}\" y=\"${n(center.pixel.y-10)}\">Sgr A* (radio reference)</text>\n")
        append("<text x=\"20\" y=\"242\" fill=\"#B4BEC5\">Standard horizon</text>\n")
        append("<text x=\"20\" y=\"462\">Galactic plane + schematic ±5° band · dashed below horizon</text>\n")
        append("<text x=\"20\" y=\"486\" fill=\"#B4BEC5\">Computed geometry; visibility depends on cloud, moonlight and light pollution.</text>\n")
        append("</g>\n</svg>\n")
    }
}
