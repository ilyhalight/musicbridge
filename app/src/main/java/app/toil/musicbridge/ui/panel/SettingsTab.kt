package app.toil.musicbridge.ui.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The settings list is a stack of sections; each section is a `LazyListScope` extension in its own file. */
@Composable
fun SettingsTab(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        serviceSection()
        vkxPromoSection()
        aboutSection()
        originalAuthorSection()
    }
}
