package dev.codexsoft.codexcine.ui.compose.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography
import dev.codexsoft.codexcine.R

/** Inter, for running text. Same files as `@font/brand_text`. */
val BrandText = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.Bold),
)

/** Space Grotesk, for titles and buttons. Same files as `@font/brand_display`. */
val BrandDisplay = FontFamily(
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

/**
 * The sizes of the XML screens: hero title 52 sp, row header 20 sp, details text 18 sp, badges 15 sp,
 * label 16 sp, buttons 20 sp.
 */
fun codexTypography() = Typography(
    displayLarge = TextStyle(fontFamily = BrandDisplay, fontWeight = FontWeight.Bold, fontSize = 52.sp),
    headlineMedium = TextStyle(fontFamily = BrandDisplay, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    titleLarge = TextStyle(fontFamily = BrandDisplay, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = BrandText, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = BrandText, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = BrandText, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = BrandText, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodySmall = TextStyle(fontFamily = BrandText, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = BrandDisplay, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    labelMedium = TextStyle(
        fontFamily = BrandDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = 0.08.em,
    ),
    labelSmall = TextStyle(fontFamily = BrandText, fontWeight = FontWeight.Medium, fontSize = 15.sp),
)
