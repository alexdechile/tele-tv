package com.tele.app.tv.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.tv.material3.Card
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults

/**
 * `Card` de tv-material con el clic **también por puntero**.
 *
 * ## Por qué existe
 *
 * `androidx.tv:tv-material:1.0.0` implementa el clic de `Surface`/`Card` con
 * `Modifier.focusable` + `Modifier.onKeyEvent` (`handleDPadEnter`), y **no** con
 * `clickable`: no hay `pointerInput` en toda la cadena. Consecuencia medida en el
 * bytecode del AAR y confirmada en una tablet Lenovo YT-X705F: un dedo (o el mouse)
 * sobre una tarjeta no dispara nada, porque el toque no produce ni foco ni tecla.
 * En la TV funciona porque el D-pad mueve el foco y `ENTER`/`DPAD_CENTER` llega al
 * `onKeyEvent`.
 *
 * ## Qué hace
 *
 * Suma un `detectTapGestures` que **no** crea un segundo nodo enfocable (eso es lo
 * que haría un `Modifier.clickable`, y rompería el orden del foco en la TV). Al
 * tocar, pide el foco para la tarjeta (de ahí sale el borde/glow ámbar que la TV
 * ya usa como señal de "estoy acá") y recién después ejecuta el `onClick`.
 *
 * - TV con D-pad: no cambia nada, el `onKeyEvent` interno sigue siendo el que
 *   dispara el clic.
 * - Tablet / mouse: el toque dispara el clic.
 * - El arrastre para scrollear sigue funcionando: un movimiento pasa el umbral
 *   táctil, el gesto de tap se cancela y el `LazyRow`/`LazyColumn` se queda con
 *   el gesto (igual que un botón de Material).
 */
@Composable
fun TeleCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.colors(),
    border: CardBorder = CardDefaults.border(),
    content: @Composable ColumnScope.() -> Unit
) {
    val focus = remember { FocusRequester() }

    Card(
        onClick = onClick,
        colors = colors,
        border = border,
        modifier = modifier
            .focusRequester(focus)
            .teleTap {
                // El foco es la señal visual. Si todavía no hay nodo enfocable
                // enganchado (raro), el clic igual tiene que ocurrir.
                runCatching { focus.requestFocus() }
                onClick()
            },
        content = content
    )
}

/** Tap que no crea un nodo enfocable (a diferencia de `Modifier.clickable`). */
fun Modifier.teleTap(onClick: () -> Unit): Modifier =
    pointerInput(onClick) {
        detectTapGestures { onClick() }
    }
