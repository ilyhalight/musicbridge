package app.toil.musicbridge.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.toil.musicbridge.MusicBridgeApplication
import app.toil.musicbridge.ui.onboarding.OnboardingScreen
import app.toil.musicbridge.ui.panel.MainPanel
import app.toil.musicbridge.util.isNotificationListenerEnabled
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class Screen { Splash, Onboarding, Panel }

@Composable
fun MusicBridgeApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by rememberSaveable { mutableStateOf(Screen.Splash) }

    LaunchedEffect(Unit) {
        if (screen == Screen.Splash) {
            val onboardingDone = MusicBridgeApplication.instance.settings.onboardingDone.first()
            screen = if (onboardingDone && context.isNotificationListenerEnabled()) Screen.Panel else Screen.Onboarding
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
            label = "root-navigation",
        ) { target ->
            when (target) {
                Screen.Splash -> Box(Modifier.fillMaxSize())
                Screen.Onboarding -> OnboardingScreen(
                    onFinished = {
                        scope.launch {
                            MusicBridgeApplication.instance.settings.setOnboardingDone()
                            screen = Screen.Panel
                        }
                    },
                )
                Screen.Panel -> MainPanel()
            }
        }
    }
}
