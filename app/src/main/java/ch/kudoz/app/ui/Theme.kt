package ch.kudoz.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kudoz.app.R

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
val KudozMutedBg = Color(0xFFF5F1E9)
val KudozSidebar = Color(0xFFFAF6EE)
val KudozSidebarAccent = Color(0xFFF1EADC)
val KudozAccent = Color(0xFFEAE8FF)
val KudozAccentInk = Color(0xFF302E50)
val KudozBounty = Color(0xFFF5AE39)
val KudozBountySoft = Color(0xFFFFE8B6)
val KudozBountyInk = Color(0xFF7A4702)

// Schriften wie auf kudoz.ch: Fredoka für Titel, Nunito für Text.
val Fredoka = FontFamily(
    Font(R.font.fredoka_500, FontWeight.Medium),
    Font(R.font.fredoka_600, FontWeight.SemiBold),
    Font(R.font.fredoka_700, FontWeight.Bold),
    Font(R.font.fredoka_700, FontWeight.Black),
)
val Nunito = FontFamily(
    Font(R.font.nunito_400, FontWeight.Normal),
    Font(R.font.nunito_600, FontWeight.Medium),
    Font(R.font.nunito_600, FontWeight.SemiBold),
    Font(R.font.nunito_700, FontWeight.Bold),
    Font(R.font.nunito_800, FontWeight.ExtraBold),
    Font(R.font.nunito_800, FontWeight.Black),
)

private val base = Typography()
private fun TextStyle.display() = copy(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold)
private fun TextStyle.body() = copy(fontFamily = Nunito)

val KudozTypography = Typography(
    displayLarge = base.displayLarge.display(), displayMedium = base.displayMedium.display(), displaySmall = base.displaySmall.display(),
    headlineLarge = base.headlineLarge.display(), headlineMedium = base.headlineMedium.display(), headlineSmall = base.headlineSmall.display(),
    titleLarge = base.titleLarge.display(), titleMedium = base.titleMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge.body(), bodyMedium = base.bodyMedium.body(), bodySmall = base.bodySmall.body(),
    labelLarge = base.labelLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
    labelSmall = base.labelSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
)

@Composable
fun KudozTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = KudozPrimary,
            onPrimary = Color(0xFFFDFCF8),
            primaryContainer = KudozAccent,
            onPrimaryContainer = KudozAccentInk,
            secondary = KudozAccentInk,
            secondaryContainer = KudozSidebarAccent,
            onSecondaryContainer = KudozInk,
            tertiary = KudozBountyInk,
            tertiaryContainer = KudozBountySoft,
            background = KudozBackground,
            onBackground = KudozInk,
            surface = KudozBackground,
            onSurface = KudozInk,
            surfaceVariant = KudozMutedBg,
            onSurfaceVariant = KudozMuted,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color.White,
            surfaceContainer = KudozSidebar,
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = KudozMutedBg,
            outline = KudozBorder,
            outlineVariant = KudozBorder,
            error = Color(0xFFE14754),
        ),
        typography = KudozTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
    ) {
        ProvideTextStyle(KudozTypography.bodyMedium, content)
    }
}

/** Weicher, violett getönter Schatten wie «shadow-soft» auf kudoz.ch. */
fun Modifier.softShadow(shape: Shape = RoundedCornerShape(16.dp), strong: Boolean = false): Modifier =
    shadow(if (strong) 14.dp else 8.dp, shape, clip = false, ambientColor = KudozPrimary.copy(alpha = 0.35f), spotColor = KudozPrimary.copy(alpha = if (strong) 0.45f else 0.3f))

/** Karte wie KCard: weiss, abgerundet, feiner Rand, weicher Schatten. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KCard(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    radius: Int = 16,
    border: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius.dp)
    val m = modifier.softShadow(shape)
    val b = if (border) BorderStroke(1.dp, KudozBorder) else null
    if (onClick != null) Card(onClick = onClick, modifier = m, shape = shape, border = b, colors = CardDefaults.cardColors(containerColor = color), content = content)
    else Card(modifier = m, shape = shape, border = b, colors = CardDefaults.cardColors(containerColor = color), content = content)
}

/** Reiter als Pillen wie die Navigation auf kudoz.ch (aktiv: beige Pille, fett). */
@Composable
fun PillTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Text(
                l,
                Modifier.clip(RoundedCornerShape(50)).background(if (on) KudozSidebarAccent else Color.Transparent)
                    .clickable { onSelect(i) }.padding(horizontal = 14.dp, vertical = 8.dp),
                fontFamily = Nunito,
                fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (on) KudozInk else KudozMuted,
                fontSize = 14.sp,
            )
        }
    }
    HorizontalDivider(color = KudozBorder)
}
