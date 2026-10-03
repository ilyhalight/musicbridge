package app.toil.musicbridge.ui.panel

import android.content.Context
import android.content.pm.PackageManager
import android.os.LocaleList
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.toil.musicbridge.R

private const val VkxPackage = "ua.itaysonlab.vkx"
private const val VkxChannelUrl = "https://t.me/vkxci"

internal fun LazyListScope.vkxPromoSection() {
    item(key = "vkx-promo") {
        val context = LocalContext.current
        if (remember { shouldShowVkxPromo(context) }) VkxPromoCard()
    }
}

private fun shouldShowVkxPromo(context: Context): Boolean {
    val isRussian = LocaleList.getDefault().toLanguageTags().startsWith("ru-")
    val isVkxInstalled = try {
        context.packageManager.getPackageInfo(VkxPackage, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
    return isRussian && !isVkxInstalled
}

@Composable
private fun VkxPromoCard() {
    val uriHandler = LocalUriHandler.current
    Card(
        onClick = { uriHandler.openUri(VkxChannelUrl) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        ListItem(
            overlineContent = { Text(stringResource(R.string.ru_vkx_ad)) },
            headlineContent = { Text(stringResource(R.string.ru_vkx_ad_1)) },
            supportingContent = { Text(stringResource(R.string.ru_vkx_ad_2)) },
            trailingContent = { Icon(painterResource(R.drawable.chevron_right_24px), contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
