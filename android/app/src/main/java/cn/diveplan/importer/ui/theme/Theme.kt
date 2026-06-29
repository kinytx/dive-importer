package cn.diveplan.importer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * DivePlan Importer 全局主题 —— 把 [DarkPalette] / [LightPalette] 映射到 Material3 ColorScheme。
 *
 * 设计原则（跟小程序 shared/app.wxss 一致）：
 *  - 安全色（warn）跨主题永远可识别 → error 槽位保持饱和红
 *  - 氦气色（accent-he）跨主题永远是琥珀/珊瑚橙 → tertiary
 *  - 主色（cyan / 礁湖青绿）按 dark/light 切换 → primary
 *
 * 不启用 dynamicColor（Android 12+ 取系统取色），因为我们要严格对齐小程序视觉。
 */
@Composable
fun DivePlanImporterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DivePlanTypography,
        content = content,
    )
}

private val DarkColors = darkColorScheme(
    primary           = DarkPalette.AccentCyan,
    onPrimary         = DarkPalette.TextOnAccent,
    primaryContainer  = DarkPalette.BgInput,
    onPrimaryContainer = DarkPalette.AccentCyan,

    secondary         = DarkPalette.AccentTeal,
    onSecondary       = DarkPalette.TextOnAccent,

    tertiary          = DarkPalette.AccentHe,
    onTertiary        = DarkPalette.TextOnAccent,

    error             = DarkPalette.AccentWarn,
    onError           = DarkPalette.TextPrimary,

    background        = DarkPalette.BgDeep,
    onBackground      = DarkPalette.TextPrimary,
    surface           = DarkPalette.BgCard,
    onSurface         = DarkPalette.TextPrimary,
    surfaceVariant    = DarkPalette.BgInput,
    onSurfaceVariant  = DarkPalette.TextSecondary,

    outline           = DarkPalette.BorderActive,
    outlineVariant    = DarkPalette.Border,
)

private val LightColors = lightColorScheme(
    primary           = LightPalette.AccentCyan,
    onPrimary         = LightPalette.TextOnAccent,
    primaryContainer  = LightPalette.BgInput,
    onPrimaryContainer = LightPalette.AccentCyan,

    secondary         = LightPalette.AccentTeal,
    onSecondary       = LightPalette.TextOnAccent,

    tertiary          = LightPalette.AccentHe,
    onTertiary        = LightPalette.TextOnAccent,

    error             = LightPalette.AccentWarn,
    onError           = LightPalette.TextOnAccent,

    background        = LightPalette.BgDeep,
    onBackground      = LightPalette.TextPrimary,
    surface           = LightPalette.BgCard,
    onSurface         = LightPalette.TextPrimary,
    surfaceVariant    = LightPalette.BgInput,
    onSurfaceVariant  = LightPalette.TextSecondary,

    outline           = LightPalette.BorderActive,
    outlineVariant    = LightPalette.Border,
)
