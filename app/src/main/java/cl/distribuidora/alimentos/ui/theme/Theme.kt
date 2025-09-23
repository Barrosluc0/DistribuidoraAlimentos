package cl.distribuidora.alimentos.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color.White,        // ← Fondo blanco forzado
    surface = Color.White,           // ← Superficie blanca
    onBackground = Color.Black,      // ← Texto negro sobre fondo
    onSurface = Color.Black          // ← Texto negro sobre superficie
)

@Composable
fun DistribuidoraAlimentosTheme(
    darkTheme: Boolean = false,      // ← Siempre tema claro
    dynamicColor: Boolean = false,   // ← Colores dinámicos desactivados
    content: @Composable () -> Unit
) {
    // Forzar siempre el esquema de colores claro
    val colorScheme = LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}