package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val TruckSimColorScheme = darkColorScheme(
    primary = AmberDiesel,
    onPrimary = ObsidianNight,
    primaryContainer = AmberDark,
    onPrimaryContainer = AmberGlow,
    secondary = CobaltBeacon,
    onSecondary = ObsidianNight,
    secondaryContainer = CabMetal,
    onSecondaryContainer = ChromeSilver,
    tertiary = EmeraldCargo,
    onTertiary = ObsidianNight,
    background = ObsidianNight,
    onBackground = ChromeSilver,
    surface = SteelSlate,
    onSurface = ChromeSilver,
    surfaceVariant = CabMetal,
    onSurfaceVariant = MutedSteel,
    outline = GunmetalBorder,
    error = CrimsonBrake,
    onError = ChromeSilver
)

private val TruckSimShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun TruckSimTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TruckSimColorScheme,
        typography = TruckSimTypography,
        shapes = TruckSimShapes,
        content = content
    )
}
