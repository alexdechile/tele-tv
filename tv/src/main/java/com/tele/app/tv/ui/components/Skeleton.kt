package com.tele.app.tv.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Esqueletos de carga ("skeleton").
 *
 * Antes cada espera era un texto centrado ("Cargando catálogo…") sobre una
 * pantalla vacía: en una TV a 3 metros eso se lee como "la app se colgó". Estos
 * bloques dibujan la **forma** de lo que va a llegar —filas de carátulas, la
 * grilla de resultados, las fuentes de un título— y laten despacio para mostrar
 * que hay trabajo en curso.
 *
 * El latido es de opacidad, no de color: así funciona igual en tema claro u
 * oscuro y no compite con el foco del control remoto.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.26f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    Box(modifier = modifier.clip(shape).background(Color.White.copy(alpha = alpha)))
}

/** Bloque de texto de una o varias líneas. */
@Composable
fun SkeletonText(
    width: Dp,
    height: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    ShimmerBox(modifier = modifier.width(width).height(height), shape = RoundedCornerShape(6.dp))
}

/** Carátula con su rótulo, del mismo tamaño que las tarjetas reales. */
@Composable
private fun SkeletonPoster(width: Dp = 180.dp, height: Dp = 270.dp) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ShimmerBox(modifier = Modifier.width(width).height(height))
        SkeletonText(width = width * 0.8f, height = 12.dp)
    }
}

/** Fila de carátulas: el esqueleto del home. */
@Composable
fun SkeletonPosterRow(
    count: Int = 6,
    posterWidth: Dp = 180.dp,
    posterHeight: Dp = 270.dp
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonText(width = 220.dp, height = 20.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            repeat(count) {
                SkeletonPoster(width = posterWidth, height = posterHeight)
            }
        }
    }
}

/** Catálogo completo: encabezado + varias filas. */
@Composable
fun SkeletonCatalog(rows: Int = 2, postersPerRow: Int = 5) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SkeletonText(width = 240.dp, height = 42.dp)
            SkeletonText(width = 320.dp, height = 18.dp)
            Spacer(modifier = Modifier.weight(1f))
            ShimmerBox(modifier = Modifier.width(240.dp).height(64.dp))
        }
        repeat(rows) {
            SkeletonPosterRow(count = postersPerRow)
        }
    }
}

/** Grilla de resultados del buscador. */
@Composable
fun SkeletonGrid(rows: Int = 3, columns: Int = 6) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(columns) {
                    ShimmerBox(modifier = Modifier.width(120.dp).height(180.dp))
                }
            }
        }
    }
}

/** Detalle: ficha a la izquierda y lista de fuentes a la derecha. */
@Composable
fun SkeletonDetail() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        ShimmerBox(modifier = Modifier.width(220.dp).height(330.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SkeletonText(width = 320.dp, height = 28.dp)
            SkeletonText(width = 200.dp, height = 16.dp)
            SkeletonText(width = 480.dp, height = 14.dp)
            SkeletonText(width = 440.dp, height = 14.dp)
            Spacer(modifier = Modifier.height(6.dp))
            repeat(3) { SkeletonStreamRow() }
        }
    }
}

/** Una fuente candidata (o un episodio) mientras se buscan. */
@Composable
fun SkeletonStreamRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBox(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonText(width = 260.dp, height = 14.dp)
            SkeletonText(width = 160.dp, height = 12.dp)
        }
    }
}
