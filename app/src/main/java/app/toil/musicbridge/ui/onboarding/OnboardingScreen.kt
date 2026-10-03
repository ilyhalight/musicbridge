package app.toil.musicbridge.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.toil.musicbridge.R
import app.toil.musicbridge.ui.common.musicBridgeHazeStyle
import app.toil.musicbridge.ui.common.withContentPadding
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private const val CardTiltDegrees = 3f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val hazeState = rememberHazeState()
    val hazeStyle = musicBridgeHazeStyle()
    val permissions = rememberPermissionState()

    var showPermissions by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showPermissions) { showPermissions = false }

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val slide by animateFloatAsState(if (entered) 0f else 200f, spring(dampingRatio = 0.75f, stiffness = 200f), label = "intro-slide")
    val fade by animateFloatAsState(if (entered) 1f else 0f, spring(dampingRatio = 1f, stiffness = 200f), label = "intro-fade")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.onboarding)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                modifier = Modifier.hazeEffect(hazeState, hazeStyle),
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().hazeEffect(hazeState, hazeStyle)) {
                OnboardingActions(
                    showPermissions = showPermissions,
                    permissions = permissions,
                    onGetStarted = { showPermissions = true },
                    onContinue = onFinished,
                    modifier = Modifier.navigationBarsPadding().padding(24.dp),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .hazeSource(hazeState)
                .graphicsLayer {
                    translationY = slide
                    alpha = fade
                },
            contentPadding = padding.withContentPadding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            introCard(R.string.onboarding_c1, null, -CardTiltDegrees)
            introCard(R.string.onboarding_c2, null, CardTiltDegrees)
            introCard(R.string.onboarding_c3, R.string.onboarding_c3_a, -CardTiltDegrees)
            introCard(R.string.onboarding_c4, R.string.onboarding_c4_a, CardTiltDegrees)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun OnboardingActions(
    showPermissions: Boolean,
    permissions: PermissionState,
    onGetStarted: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SharedTransitionLayout(modifier) {
        AnimatedContent(targetState = showPermissions, label = "onboarding-actions") { permissionsPage ->
            val bounds = Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState("onboarding-container"),
                animatedVisibilityScope = this@AnimatedContent,
            )
            if (permissionsPage) {
                PermissionsCard(permissions, onContinue, bounds.fillMaxWidth())
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        onClick = onGetStarted,
                        modifier = bounds.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        Text(stringResource(R.string.onboarding_act))
                    }
                    Text(
                        text = stringResource(R.string.onboarding_act_disclaimer),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
