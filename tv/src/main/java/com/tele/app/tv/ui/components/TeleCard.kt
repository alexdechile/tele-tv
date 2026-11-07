package com.tele.app.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardScale

/**
 * `Card` de tv-material con el clic **también por puntero** y foco de alto contraste para TV.
 *
 * En salas de estar a 3-4 metros, un botón con foco tiene que ser inconfundible:
 * resalta con borde blanco de 2.5dp, fondo lavanda pastel brillante, texto oscuro y leve escala.
 */
@Composable
fun TeleCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.colors(
        containerColor = Color(0xF0182238),
        focusedContainerColor = Color(0xFFC7B9FF),
        contentColor = Color.White,
        focusedContentColor = Color(0xFF241B44)
    ),
    border: CardBorder = CardDefaults.border(
        border = Border(BorderStroke(1.dp, Color(0x33C7B9FF))),
        focusedBorder = Border(BorderStroke(2.5.dp, Color.White))
    ),
    scale: CardScale = CardDefaults.scale(focusedScale = 1.04f),
    content: @Composable ColumnScope.() -> Unit
) {
    val focus = remember { FocusRequester() }

    Card(
        onClick = onClick,
        colors = colors,
        border = border,
        scale = scale,
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
