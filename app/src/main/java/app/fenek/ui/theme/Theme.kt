package app.fenek.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Brand palette: violet of the app icon and the sandy fur of the mascot. */
private val LightColors = lightColorScheme(
    primary = Color(0xFF5B45E0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5DEFF),
    onPrimaryContainer = Color(0xFF1B0066),
    secondary = Color(0xFF5E5A71),
    secondaryContainer = Color(0xFFE4DFF9),
    onSecondaryContainer = Color(0xFF1B192C),
    tertiary = Color(0xFF8C4F1F),
    tertiaryContainer = Color(0xFFFFDCC2),
    onTertiaryContainer = Color(0xFF331200),
    background = Color(0xFFFCF8FF),
    surface = Color(0xFFFCF8FF),
    surfaceContainer = Color(0xFFF1ECF8),
    surfaceContainerHigh = Color(0xFFEBE6F3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC8BFFF),
    onPrimary = Color(0xFF2C0F9E),
    primaryContainer = Color(0xFF4329C7),
    onPrimaryContainer = Color(0xFFE5DEFF),
    secondary = Color(0xFFC8C3DC),
    secondaryContainer = Color(0xFF474459),
    onSecondaryContainer = Color(0xFFE4DFF9),
    tertiary = Color(0xFFFFB77F),
    tertiaryContainer = Color(0xFF6E380A),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF131218),
    surface = Color(0xFF131218),
    surfaceContainer = Color(0xFF201F25),
    surfaceContainerHigh = Color(0xFF2A2930),
)

@Composable
fun FenekTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

/** Distinct, readable colours for the participants of a dialogue. */
object SpeakerColors {
    private val light = listOf(
        Color(0xFF5B45E0), Color(0xFFC2410C), Color(0xFF15803D), Color(0xFFB4237A),
        Color(0xFF0E7490), Color(0xFFA16207), Color(0xFFB91C1C), Color(0xFF4D7C0F),
    )
    private val dark = listOf(
        Color(0xFFC8BFFF), Color(0xFFFDBA74), Color(0xFF86EFAC), Color(0xFFF9A8D4),
        Color(0xFF67E8F9), Color(0xFFFDE047), Color(0xFFFCA5A5), Color(0xFFBEF264),
    )

    @Composable
    fun of(speaker: Int): Color {
        val palette = if (isSystemInDarkTheme()) dark else light
        return palette[speaker.mod(palette.size)]
    }
}
