package app.toil.musicbridge.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.DynamicMaterialTheme
import com.materialkolor.PaletteStyle

private val SeedColor = Color(0xFFFF9230)

/** Always dark; the scheme is generated from a fixed orange seed rather than the wallpaper. */
@Composable
fun MusicBridgeTheme(content: @Composable () -> Unit) {
    DynamicMaterialTheme(
        seedColor = SeedColor,
        isDark = true,
        style = PaletteStyle.TonalSpot,
        content = content,
    )
}
