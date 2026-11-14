package dev.codexsoft.codexcine.ui.compose.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme

/**
 * The rounded button used everywhere (`bg_pill`, `bg_pill_primary`): a translucent pill that turns ivory
 * with focus, or an amber one when [primary]. Its text and icon are dark on the light pill, light otherwise.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    @DrawableRes icon: Int? = null,
    height: Dp = 52.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    description: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = CodexTheme.colors
    val background = when {
        focused -> colors.ivory
        primary -> colors.amber
        else -> colors.pill
    }
    val content = if (focused || primary) colors.night else colors.ivory
    Row(
        modifier = modifier
            .height(height)
            .dpadClickable(onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .semantics { description?.let { contentDescription = it } }
            .background(background, RoundedCornerShape(percent = 50))
            .padding(start = if (icon != null) 22.dp else 28.dp, end = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(content),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = textStyle, color = content)
    }
}
