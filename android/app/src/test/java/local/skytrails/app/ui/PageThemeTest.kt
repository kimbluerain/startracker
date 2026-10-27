package local.skytrails.app.ui

import org.junit.Assert.*
import org.junit.Test

class PageThemeTest {
 @Test fun dataPagesFollowSystemAndExplicitSettingsWhileObservationStaysDark() {
  for(tab in listOf(AppTab.PLAN,AppTab.PLACES)) {
   assertEquals(AppThemeMode.LIGHT,effectivePageTheme(AppThemeMode.SYSTEM,tab,false))
   assertEquals(AppThemeMode.DARK,effectivePageTheme(AppThemeMode.SYSTEM,tab,true))
   for(systemDark in listOf(false,true))for(mode in AppThemeMode.entries.filter{it!=AppThemeMode.SYSTEM}) {
    assertEquals(mode,effectivePageTheme(mode,tab,systemDark))
    assertEquals(mode==AppThemeMode.LIGHT||mode==AppThemeMode.OUTDOOR,usesDarkSystemIcons(effectivePageTheme(mode,tab,systemDark)))
   }
  }
  for(mode in AppThemeMode.entries)for(systemDark in listOf(false,true)) {
   val effective=effectivePageTheme(mode,AppTab.OBSERVE,systemDark)
   assertEquals(if(mode==AppThemeMode.NIGHT)AppThemeMode.NIGHT else AppThemeMode.DARK,effective)
   assertFalse(usesDarkSystemIcons(effective))
  }
 }
 @Test fun nightConversionRemovesBlueAndGreenEvenForSaturatedInputsAndKeepsAlpha() {
  for(input in listOf(0xff0000ff,0xff00ff00,0xff00ffff,0xffffffff,0xffff0000,0x800080ff,0xff000000)) {
   val output=nightBackdropPixel(input.toInt());val r=(output ushr 16)and 255;val g=(output ushr 8)and 255;val b=output and 255
   assertEquals(input.toInt() ushr 24,output ushr 24)
   assertTrue(g<=r*.12);assertTrue(b<=r*.08)
   if(input!=0xff000000)assertTrue(r>0)
  }
  // White conversion is deliberately dim, not a bright red flash.
  assertTrue(((nightBackdropPixel(-1) ushr 16)and 255)<=153)
 }
}
