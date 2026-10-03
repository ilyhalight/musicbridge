package app.toil.musicbridge.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

internal fun LazyListScope.introCard(@StringRes title: Int, @StringRes description: Int?, tiltDegrees: Float) {
    item {
        IntroCard(
            title = stringResource(title),
            description = description?.let { stringResource(it) },
            modifier = Modifier.fillMaxWidth().graphicsLayer { rotationZ = tiltDegrees },
        )
    }
}

@Composable
private fun IntroCard(title: String, description: String?, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            if (description != null) {
                Text(
                    text = description,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
