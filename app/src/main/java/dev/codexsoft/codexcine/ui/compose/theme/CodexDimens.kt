package dev.codexsoft.codexcine.ui.compose.theme

import androidx.compose.ui.unit.dp

/** Sizes shared by the screens, with the values of `res/values/dimens.xml` and of the XML layouts. */
object CodexDimens {
    val cardCornerRadius = 10.dp

    /** Start padding of the hero blocks (home billboard, details). */
    val heroPaddingStart = 58.dp
    val heroPaddingEnd = 48.dp
    val detailsHeroHeight = 470.dp
    val homeHeroHeight = 340.dp

    /** The margins that keep content inside the area every TV shows (overscan). */
    val screenPaddingHorizontal = 48.dp
    val screenPaddingVertical = 27.dp

    val rowGap = 16.dp
    val cardGap = 16.dp
    val textColumnWidth = 720.dp
}
