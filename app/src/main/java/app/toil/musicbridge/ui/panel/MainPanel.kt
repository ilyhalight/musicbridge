package app.toil.musicbridge.ui.panel

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.toil.musicbridge.R
import app.toil.musicbridge.ui.common.musicBridgeHazeStyle
import app.toil.musicbridge.ui.common.withContentPadding
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private enum class PanelTab(@param:StringRes val title: Int, @param:DrawableRes val icon: Int) {
    Apps(R.string.panel_apps, R.drawable.app_registration_24px),
    Settings(R.string.panel_settings, R.drawable.settings_24px),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPanel() {
    var tab by rememberSaveable { mutableStateOf(PanelTab.Settings) }
    val hazeState = rememberHazeState()
    val hazeStyle = musicBridgeHazeStyle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(tab.title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                modifier = Modifier.hazeEffect(hazeState, hazeStyle),
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    windowInsets = WindowInsets(0),
                    modifier = Modifier.clip(CircleShape).hazeEffect(hazeState, hazeStyle),
                ) {
                    PanelTab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = { Icon(painterResource(entry.icon), contentDescription = null) },
                            label = { Text(stringResource(entry.title)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "panel-tab",
        ) { target ->
            when (target) {
                PanelTab.Apps -> AppsTab()
                PanelTab.Settings -> SettingsTab(contentPadding = padding.withContentPadding(horizontal = 24.dp, vertical = 16.dp))
            }
        }
    }
}
