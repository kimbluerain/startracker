package local.skytrails.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SunGold=Color(0xFFEAC58A)
val MoonSilver=Color(0xFFF2F0ED)
val GalaxyTeal=Color(0xFFD7C8B5)
val Ink=Color(0xFF252628)
val NightRed=Color(0xFFFF7564)
private fun skyColors(dark:Boolean,red:Boolean,outdoor:Boolean=false):ColorScheme {
 val foreground=if(outdoor)Color.Black else if(red)NightRed else if(dark)MoonSilver else Ink
 val background=if(outdoor)Color.White else if(red)Color(0xFF140706) else if(dark)Color(0xFF101216) else Color(0xFFF0EEEA)
 val surface=if(outdoor)Color.White else if(red)Color(0xFF24100D) else if(dark)Color(0xFF24262B) else Color(0xFFF7F5F1)
 val raised=if(red)Color(0xFF361712) else if(dark)Color(0xFF35373C) else Color(0xFFE3DED6)
 val accent=if(red)NightRed else if(dark)SunGold else Color(0xFF71542E)
 val onAccent=if(red)Color(0xFF180300) else if(dark)Ink else Color.White
 val base=if(dark||red)darkColorScheme() else lightColorScheme()
 return base.copy(primary=accent,onPrimary=onAccent,primaryContainer=raised,onPrimaryContainer=foreground,
  secondary=accent,onSecondary=onAccent,secondaryContainer=raised,onSecondaryContainer=foreground,
  tertiary=accent,onTertiary=onAccent,tertiaryContainer=raised,onTertiaryContainer=foreground,
  background=background,onBackground=foreground,surface=surface,onSurface=foreground,surfaceVariant=raised,onSurfaceVariant=foreground.copy(alpha=.7f),
  surfaceTint=accent,inverseSurface=foreground,inverseOnSurface=background,inversePrimary=accent,
  surfaceBright=raised,surfaceDim=background,surfaceContainerLowest=background,surfaceContainerLow=surface,surfaceContainer=surface,surfaceContainerHigh=raised,surfaceContainerHighest=raised,
  outline=foreground.copy(alpha=.45f),outlineVariant=foreground.copy(alpha=.17f),error=if(red)NightRed else Color(0xFFEF9A88),onError=onAccent,errorContainer=raised,onErrorContainer=foreground)
}
private val SkyTypography=Typography(
 displayLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Light,fontSize=58.sp,letterSpacing=(-2).sp),
 headlineLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=36.sp,letterSpacing=(-1).sp),
 titleLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium,fontSize=22.sp),
 bodyLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=16.sp,lineHeight=23.sp),
 bodyMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=14.sp,lineHeight=20.sp),
 labelLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium,fontSize=14.sp)
)
@Composable fun SkyTheme(mode:AppThemeMode,content:@Composable ()->Unit) {
 val dark=mode==AppThemeMode.DARK || mode==AppThemeMode.NIGHT || (mode==AppThemeMode.SYSTEM && isSystemInDarkTheme())
 val colors=skyColors(dark,mode==AppThemeMode.NIGHT,mode==AppThemeMode.OUTDOOR)
 MaterialTheme(colorScheme=colors,typography=SkyTypography) { CompositionLocalProvider(LocalContentColor provides colors.onBackground,LocalLightGlass provides !dark) { content() } }
}
fun targetName(t:SkyTarget)=when(t){SkyTarget.SUN->"太阳";SkyTarget.MOON->"月亮";SkyTarget.GALAXY->"银河"}
fun targetColor(t:SkyTarget,night:Boolean)=if(night)NightRed else when(t){SkyTarget.SUN->SunGold;SkyTarget.MOON->MoonSilver;SkyTarget.GALAXY->GalaxyTeal}
@Composable fun DarkGlassContent(night:Boolean,content:@Composable ()->Unit) {
 val colors=skyColors(true,night)
 MaterialTheme(colorScheme=colors,typography=SkyTypography) { CompositionLocalProvider(LocalContentColor provides colors.onSurface,LocalLightGlass provides false) {content()} }
}

val LocalLightGlass=staticCompositionLocalOf{false}
/** Observation keeps its dark photographic canvas; data pages honour the saved setting. */
fun effectivePageTheme(mode:AppThemeMode,tab:AppTab,systemDark:Boolean):AppThemeMode = when {
 mode==AppThemeMode.NIGHT->AppThemeMode.NIGHT
 tab==AppTab.OBSERVE->AppThemeMode.DARK
 mode==AppThemeMode.SYSTEM->if(systemDark)AppThemeMode.DARK else AppThemeMode.LIGHT
 else->mode
}
fun usesDarkSystemIcons(theme:AppThemeMode)=theme==AppThemeMode.LIGHT||theme==AppThemeMode.OUTDOOR
@Composable fun pageTheme(s:AppState)=effectivePageTheme(s.theme,s.tab,isSystemInDarkTheme())
@Composable fun PageGlassContent(s:AppState,content:@Composable ()->Unit) {SkyTheme(pageTheme(s),content)}
