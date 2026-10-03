package app.toil.musicbridge.ui.panel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.toil.musicbridge.R

private const val AuthorUrl = "https://github.com/ilyhalight/"
private const val StudioUrl = "https://t.me/bruhcollective"

/** The card about this fork: current version and its author. */
internal fun LazyListScope.aboutSection() {
    item(key = "about") { AboutCard() }
}

/** The card about the app this one is forked from. */
internal fun LazyListScope.originalAuthorSection() {
    item(key = "about-original") { OriginalAuthorCard() }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }

    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column {
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                },
                supportingContent = {
                    Column {
                        Text(stringResource(R.string.about_current_version, version.orEmpty()))
                        Text(stringResource(R.string.about_current_author))
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            TextButton(
                onClick = { uriHandler.openUri(AuthorUrl) },
                modifier = Modifier.align(Alignment.End).padding(end = 8.dp, bottom = 8.dp),
            ) {
                Icon(painterResource(R.drawable.ic_github), contentDescription = null)
                Text(stringResource(R.string.about_current_github), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun OriginalAuthorCard() {
    val uriHandler = LocalUriHandler.current

    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column {
            ListItem(
                overlineContent = { Text(stringResource(R.string.about_original_subtitle)) },
                headlineContent = {
                    Text(stringResource(R.string.about_original_title), fontWeight = FontWeight.Bold)
                },
                supportingContent = { Text(stringResource(R.string.about_original_author)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            TextButton(
                onClick = { uriHandler.openUri(StudioUrl) },
                modifier = Modifier.align(Alignment.End).padding(end = 8.dp, bottom = 8.dp),
            ) {
                Text(stringResource(R.string.about_original_studio))
                Icon(painterResource(R.drawable.chevron_right_24px), contentDescription = null)
            }
        }
    }
}
