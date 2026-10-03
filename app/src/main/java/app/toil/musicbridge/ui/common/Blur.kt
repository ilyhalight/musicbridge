package app.toil.musicbridge.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/** Nearly opaque surface-tinted blur used by the top and bottom bars. */
@Composable
fun musicBridgeHazeStyle(): HazeStyle {
    val surface = MaterialTheme.colorScheme.surface
    val tintAlpha = if (surface.luminance() >= 0.5f) 0.92f else 0.97f
    return HazeStyle(
        backgroundColor = surface,
        tint = HazeTint(surface.copy(alpha = tintAlpha)),
        blurRadius = 24.dp,
    )
}

/** Adds [horizontal] and [vertical] on top of scaffold insets so list content scrolls under blurred bars. */
@Composable
fun PaddingValues.withContentPadding(horizontal: Dp = 24.dp, vertical: Dp = 16.dp): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues.Absolute(
        left = calculateLeftPadding(direction) + horizontal,
        top = calculateTopPadding() + vertical,
        right = calculateRightPadding(direction) + horizontal,
        bottom = calculateBottomPadding() + vertical,
    )
}
