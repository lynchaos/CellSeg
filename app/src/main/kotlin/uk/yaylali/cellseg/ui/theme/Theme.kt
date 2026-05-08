package uk.yaylali.cellseg.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Teal900,
    onPrimary = Cream50,
    primaryContainer = TealLight,
    onPrimaryContainer = Charcoal,
    secondary = Coral500,
    onSecondary = Cream50,
    secondaryContainer = CoralLight,
    onSecondaryContainer = Charcoal,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = SurfaceDark,
    primaryContainer = Teal900,
    onPrimaryContainer = Cream50,
    secondary = CoralLight,
    onSecondary = SurfaceDark,
    secondaryContainer = Coral500,
    onSecondaryContainer = Cream50,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = Charcoal,
    onSurface = OnSurfaceDark,
)

@Composable
fun CellSegTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CellSegTypography,
        content = content,
    )
}
