package com.tele.app.tv.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Paleta y degradados de colores pasteles relajantes para Tele TV.
 *
 * Busca transmitir serenidad y confort visual en ambientes oscuros de sala de estar (10-foot UI),
 * alejándose de colores estridentes como el amarillo ámbar puro o el negro plano.
 */
object PastelTheme {

    // --- Colores Pasteles Tranquilizantes ---
    val Lavender = Color(0xFFC7B9FF)        // Calma y elegancia, foco principal
    val LavenderLight = Color(0xFFEDE9FE)   // Anillo de foco suave y texto destacado
    val LavenderDark = Color(0xFF241B44)    // Texto sobre botón lavanda

    val Mint = Color(0xFFA7F3D0)            // Seguridad y satisfacción (Guardar/Conservar)
    val MintLight = Color(0xFFD1FAE5)
    val MintDark = Color(0xFF064E3B)        // Texto sobre botón menta

    val Sky = Color(0xFFBAE6FD)             // Claridad mental y serenidad (Buscador/Filtros)
    val SkyLight = Color(0xFFE0F2FE)
    val SkyDark = Color(0xFF0C4A6E)

    val Peach = Color(0xFFFED7AA)           // Calidez tranquila
    val Coral = Color(0xFFFECDD3)           // Alerta suave no punitiva (Eliminar)
    val CoralDark = Color(0xFF881337)

    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFFCBD5E1)
    val TextMuted = Color(0xFF94A3B8)

    val SurfaceOverlay = Color(0x331E293B)
    val SurfaceBorder = Color(0x33C7B9FF)

    // --- Degradados que Tranquilizan ---

    /**
     * Fondo ambiental de toda la app: elimina el negro muerto y sumerge la pantalla
     * en un degradado noche relajante (azul noche -> pizarra suave -> lila noche).
     */
    val AppAmbientGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0B0F19),
            Color(0xFF101726),
            Color(0xFF16142A)
        )
    )

    /** Degradado para el panel de controles del reproductor (transparente a noche pastel). */
    val PlayerBarGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0x000B0F19),
            Color(0xD90E1524),
            Color(0xF8151A30)
        )
    )

    /** Degradado suave para la barra de seek / progreso. */
    val SeekProgressGradient = Brush.horizontalGradient(
        colors = listOf(Mint, Sky, Lavender)
    )

    /** Degradado de foco activo para botones principales. */
    val FocusGradient = Brush.horizontalGradient(
        colors = listOf(Lavender, Sky)
    )

    /** Degradado para píldora de conservar archivo (Menta a Celeste suave). */
    val KeepPillGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF86EFAC), Mint)
    )

    /** Degradado para píldora de eliminar archivo (Coral a Durazno pastel). */
    val DeletePillGradient = Brush.horizontalGradient(
        colors = listOf(Coral, Peach)
    )
}
