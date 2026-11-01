package com.lumora.player

import androidx.compose.foundation.background
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/** A full UI theme. Image themes (img != null) paint the picture behind the screens, with colors taken from it. */
class ThemeSpec(
    val name: String,
    val scheme: ColorScheme,
    val img: Int? = null,
    val focusX: Float = 0.5f,
    val scrim: Color = Color(0x99000000)
)

val LocalThemeSpec = compositionLocalOf<ThemeSpec?> { null }

private fun imageTheme(
    name: String, img: Int, focusX: Float, accent: Long, bg: Long, surface: Long, variant: Long, scrimAlpha: Int = 0xA6
) = ThemeSpec(
    name = name,
    img = img,
    focusX = focusX,
    scrim = Color((scrimAlpha shl 24) or (bg and 0xFFFFFF).toInt()),
    scheme = darkColorScheme(
        primary = Color(accent), onPrimary = Color.White, secondary = Color(accent),
        background = Color(bg), surface = Color(surface),
        onSurface = Color(0xFFF1F3FA), onBackground = Color(0xFFF1F3FA),
        surfaceVariant = Color(variant), onSurfaceVariant = Color(0xFFC4CAD8)
    )
)

/** Index 0..2 are the original themes (built in MainActivity); 3.. are the image themes below. */
val IMAGE_THEMES: List<ThemeSpec> = listOf(
    imageTheme("Blue Sky", R.drawable.theme_bg_1, 0.62f, 0xFF3B9CFF, 0xFF08182E, 0xFF0E2744, 0xFF16365C, 0x99),
    imageTheme("Upside-Down Sky", R.drawable.theme_bg_2, 0.55f, 0xFFFFA45C, 0xFF0A1030, 0xFF141C45, 0xFF1E2A5E, 0x99),
    imageTheme("Violet Street", R.drawable.theme_bg_3, 0.55f, 0xFFA569FF, 0xFF14121F, 0xFF1F1B30, 0xFF2B2542),
    imageTheme("Neon City", R.drawable.theme_bg_4, 0.40f, 0xFFFF3DA8, 0xFF0B0617, 0xFF170C2B, 0xFF251442),
    imageTheme("Dusk Rooftop", R.drawable.theme_bg_5, 0.52f, 0xFFFF9F8A, 0xFF0A1236, 0xFF121C4A, 0xFF1C2A63, 0x99),
    imageTheme("Naruto · Sage Gold", R.drawable.theme_bg_6, 0.5f, 0xFFFFB300, 0xFF0F0A06, 0xFF1D140A, 0xFF2C1E0F),
    imageTheme("Naruto · Rasengan", R.drawable.theme_bg_7, 0.5f, 0xFFFF8A00, 0xFF0E1A22, 0xFF18262F, 0xFF22343F, 0xB3)
)

const val BASE_THEMES = 3
val THEME_COUNT = BASE_THEMES + IMAGE_THEMES.size

/** Screen background: the theme picture with a tinted scrim, or the plain background color. */
@Composable
fun Modifier.themeBackground(): Modifier {
    val spec = LocalThemeSpec.current
    val img = spec?.img ?: return this.background(MaterialTheme.colorScheme.background)
    return this.background(Color.Black)
        .paint(painterResource(img), sizeToIntrinsics = false, alignment = BiasAlignment(spec.focusX * 2f - 1f, 0f), contentScale = ContentScale.Crop)
        .background(spec.scrim)
}
