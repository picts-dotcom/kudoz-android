package ch.kudoz.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Farben aus kudoz.ch (src/styles.css, oklch → sRGB umgerechnet).
val PALETTE: Map<String, Pair<Long, Long>> = mapOf(
    "mint" to (0xFFB3EDD3 to 0xFF005C41),
    "peach" to (0xFFFFD3BB to 0xFF8F4622),
    "lavender" to (0xFFDAD0FC to 0xFF5B4590),
    "sky" to (0xFFB8E5FF to 0xFF085B87),
    "lemon" to (0xFFFCF1AA to 0xFF6E5D14),
    "rose" to (0xFFFFCEDC to 0xFF9D3D59),
    "teal" to (0xFF89E5D9 to 0xFF005048),
    "coral" to (0xFFFFB4AD to 0xFF902828),
    "lilac" to (0xFFFACCFC to 0xFF6F3473),
    "lime" to (0xFFCEF18F to 0xFF41560A),
    "ocean" to (0xFF9BCCFF to 0xFF00437F),
    "silver" to (0xFFD8DEE8 to 0xFF404855),
    "apricot" to (0xFFFFC78D to 0xFF653400),
    "berry" to (0xFFF89DC9 to 0xFF68284A),
    "pistachio" to (0xFFB2E0A8 to 0xFF214F16),
    "aqua" to (0xFFA0F1FB to 0xFF00505B),
    "periwinkle" to (0xFFB9C6FF to 0xFF343C77),
    "blush" to (0xFFFFE0E1 to 0xFF6D2731),
    "sand" to (0xFFF1DFBC to 0xFF5B4320),
    "mauve" to (0xFFD3C6F0 to 0xFF4F3B71),
    "forest" to (0xFF90CEAC to 0xFF004429),
)

/** Hintergrund- und Schriftfarbe für einen Palettenschlüssel oder eine eigene Hex-Farbe. */
fun paletteColors(key: String?): Pair<Color, Color> {
    PALETTE[key]?.let { return Color(it.first) to Color(it.second) }
    if (key != null && Regex("^#[0-9a-fA-F]{6}$").matches(key)) {
        val c = Color(0xFF000000 or key.substring(1).toLong(16))
        val lum = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
        return c to if (lum > 0.6f) Color(0xFF262738) else Color.White
    }
    return Color(PALETTE.getValue("lavender").first) to Color(PALETTE.getValue("lavender").second)
}

val KudozBackground = Color(0xFFFDFAF4)
val KudozInk = Color(0xFF262738)
val KudozPrimary = Color(0xFF6B61C4)
val KudozMuted = Color(0xFF666878)
val KudozBorder = Color(0xFFE9E4DA)

@Composable
fun KudozTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = KudozPrimary,
            onPrimary = Color(0xFFFDFCF8),
            primaryContainer = Color(0xFFDAD0FC),
            onPrimaryContainer = Color(0xFF5B4590),
            secondary = Color(0xFF005C41),
            secondaryContainer = Color(0xFFB3EDD3),
            background = KudozBackground,
            onBackground = KudozInk,
            surface = KudozBackground,
            onSurface = KudozInk,
            surfaceVariant = Color(0xFFF3EEE4),
            onSurfaceVariant = KudozMuted,
            outline = KudozBorder,
            error = Color(0xFF9D3D59),
        ),
        content = content,
    )
}
