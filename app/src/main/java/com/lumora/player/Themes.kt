package com.lumora.player

import androidx.compose.foundation.background
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The layout family of a theme. Most themes keep the classic Lumore layout; the newer themes
 * change the way the home screen and navigation are arranged, not just their colours.
 */
enum class UiLayout { CLASSIC, CINEMA, GLASS, NEON }

/**
 * A full UI theme. Image themes (img != null) paint a picture behind the screens; gradient themes
 * paint a soft gradient instead. [layout] selects a completely different home/navigation style.
 */
class ThemeSpec(
    val name: String,
    val scheme: ColorScheme,
    val img: Int? = null,
    val focusX: Float = 0.5f,
    val scrim: Color = Color(0x99000000),
    val layout: UiLayout = UiLayout.CLASSIC,
    val gradient: List<Color>? = null,
    val tagline: String = ""
) {
    val isLight: Boolean get() = scheme.background.luminance() > 0.5f

    /** Corner radius used by cards of this theme. */
    val corner: Dp
        get() = when (layout) {
            UiLayout.CINEMA -> 10.dp
            UiLayout.GLASS -> 24.dp
            UiLayout.NEON -> 6.dp
            UiLayout.CLASSIC -> 16.dp
        }

    /** True when the theme has a picture or gradient behind the content. */
    val painted: Boolean get() = img != null || gradient != null
}

val LocalThemeSpec = compositionLocalOf<ThemeSpec?> { null }

/** The active layout family. Classic when no theme is active. */
@Composable
fun currentLayout(): UiLayout = LocalThemeSpec.current?.layout ?: UiLayout.CLASSIC

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

/** The three original, always-present themes. Kept byte-for-byte compatible with older installs. */
fun baseTheme(index: Int, accent: Color): ThemeSpec = when (index) {
    1 -> ThemeSpec(
        name = "Midnight Blue",
        tagline = "Deep blue, calm and focused",
        scheme = darkColorScheme(
            primary = accent, onPrimary = Color.White, secondary = Color(0xFF64B5F6),
            background = Color(0xFF06111F), surface = Color(0xFF0B1B2D),
            onSurface = Color(0xFFE8F2FF), onBackground = Color(0xFFE8F2FF),
            surfaceVariant = Color(0xFF152B43), onSurfaceVariant = Color(0xFFB7CBE0)
        )
    )
    2 -> ThemeSpec(
        name = "Light Premium",
        tagline = "A bright, airy living room",
        scheme = lightColorScheme(
            primary = accent, onPrimary = Color.White, secondary = Color(0xFF52627A),
            background = Color(0xFFF7F8FC), surface = Color.White,
            onSurface = Color(0xFF202432), onBackground = Color(0xFF202432),
            surfaceVariant = Color(0xFFE9ECF4), onSurfaceVariant = Color(0xFF50586B)
        )
    )
    else -> ThemeSpec(
        name = "Cinema Dark",
        tagline = "The original Lumore look",
        scheme = darkColorScheme(
            primary = accent, onPrimary = Color.White, secondary = accent,
            background = Color(0xFF09090E), surface = Color(0xFF13131A),
            onSurface = Color(0xFFEDEFF5), onBackground = Color(0xFFEDEFF5),
            surfaceVariant = Color(0xFF1D1D27), onSurfaceVariant = Color(0xFFB8BBC7)
        )
    )
}

/** Index 3.. are the original picture themes; their order and names are unchanged. */
val IMAGE_THEMES: List<ThemeSpec> = listOf(
    imageTheme("Blue Sky", R.drawable.theme_bg_1, 0.5f, 0xFF3B9CFF, 0xFF08182E, 0xFF0E2744, 0xFF16365C, 0x99),
    imageTheme("Upside-Down Sky", R.drawable.theme_bg_2, 0.5f, 0xFFFFA45C, 0xFF0A1030, 0xFF141C45, 0xFF1E2A5E, 0x99),
    imageTheme("Violet Street", R.drawable.theme_bg_3, 0.5f, 0xFFA569FF, 0xFF14121F, 0xFF1F1B30, 0xFF2B2542),
    imageTheme("Neon City", R.drawable.theme_bg_4, 0.5f, 0xFFFF3DA8, 0xFF0B0617, 0xFF170C2B, 0xFF251442),
    imageTheme("Dusk Rooftop", R.drawable.theme_bg_5, 0.5f, 0xFFFF9F8A, 0xFF0A1236, 0xFF121C4A, 0xFF1C2A63, 0x99),
    imageTheme("Naruto · Sage Gold", R.drawable.theme_bg_6, 0.5f, 0xFFFFB300, 0xFF0F0A06, 0xFF1D140A, 0xFF2C1E0F),
    imageTheme("Naruto · Rasengan", R.drawable.theme_bg_7, 0.5f, 0xFFFF8A00, 0xFF0E1A22, 0xFF18262F, 0xFF22343F, 0xB3)
)

/**
 * Brand-new themes. Each one ships a different layout family so switching theme changes the
 * whole interface, not only its colours.
 */
val NEW_THEMES: List<ThemeSpec> = listOf(
    // 1. A light, glassy interface with a list-first home screen.
    ThemeSpec(
        name = "Lumore Aurora",
        tagline = "Glass, daylight and soft gradients",
        layout = UiLayout.GLASS,
        gradient = listOf(Color(0xFFE9FBF6), Color(0xFFF2ECFF), Color(0xFFF9FBFF)),
        scheme = lightColorScheme(
            primary = Color(0xFF0E9F8E), onPrimary = Color.White,
            secondary = Color(0xFF7C5CFC), onSecondary = Color.White,
            background = Color(0xFFF3FBF9), surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF102220), onBackground = Color(0xFF102220),
            surfaceVariant = Color(0xFFE3F2EF), onSurfaceVariant = Color(0xFF48625E)
        )
    ),
    // 2. A warm, widescreen cinema interface with top navigation and a big hero.
    ThemeSpec(
        name = "Lumore Cinema",
        tagline = "A widescreen theatre in your pocket",
        layout = UiLayout.CINEMA,
        gradient = listOf(Color(0xFF1C130A), Color(0xFF0C0805), Color(0xFF050403)),
        scheme = darkColorScheme(
            primary = Color(0xFFF0C060), onPrimary = Color(0xFF201607),
            secondary = Color(0xFFE0894A), onSecondary = Color(0xFF1A0E05),
            background = Color(0xFF070604), surface = Color(0xFF15100A),
            onSurface = Color(0xFFF6EEDF), onBackground = Color(0xFFF6EEDF),
            surfaceVariant = Color(0xFF231A10), onSurfaceVariant = Color(0xFFCDBBA2)
        )
    ),
    // 3. A dark neon dashboard with glowing tiles and bottom pill navigation.
    ThemeSpec(
        name = "Lumore Neon",
        tagline = "Late-night arcade energy",
        layout = UiLayout.NEON,
        gradient = listOf(Color(0xFF160726), Color(0xFF0A0614), Color(0xFF040309)),
        scheme = darkColorScheme(
            primary = Color(0xFFFF2E88), onPrimary = Color.White,
            secondary = Color(0xFF22D3EE), onSecondary = Color(0xFF04212A),
            background = Color(0xFF05050A), surface = Color(0xFF0E0E1C),
            onSurface = Color(0xFFE9EDFF), onBackground = Color(0xFFE9EDFF),
            surfaceVariant = Color(0xFF191A2E), onSurfaceVariant = Color(0xFFA9AED0)
        )
    )
)

const val BASE_THEMES = 3

val THEME_COUNT = BASE_THEMES + IMAGE_THEMES.size + NEW_THEMES.size

/** Resolves a stored theme index into a full spec. Indices 0..2 depend on the accent colour. */
fun themeAt(index: Int, accent: Color): ThemeSpec {
    val i = index.coerceIn(0, THEME_COUNT - 1)
    return when {
        i < BASE_THEMES -> baseTheme(i, accent)
        i < BASE_THEMES + IMAGE_THEMES.size -> IMAGE_THEMES[i - BASE_THEMES]
        else -> NEW_THEMES[(i - BASE_THEMES - IMAGE_THEMES.size).coerceIn(0, NEW_THEMES.size - 1)]
    }
}

/** Screen background: picture, soft gradient, or the plain background colour of the theme. */
@Composable
fun Modifier.themeBackground(): Modifier {
    val spec = LocalThemeSpec.current
    val gradient = spec?.gradient
    if (gradient != null) return this.background(Brush.linearGradient(gradient))
    val img = spec?.img
    if (img != null) {
        return this.background(Color.Black)
            .paint(
                painterResource(img), sizeToIntrinsics = false,
                alignment = BiasAlignment(spec.focusX * 2f - 1f, 0f), contentScale = ContentScale.Crop
            )
            .background(spec.scrim)
    }
    return this.background(MaterialTheme.colorScheme.background)
}
