package com.tele.app.tv.ui.detail

import androidx.compose.runtime.Composable
import androidx.tv.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize

@Composable
fun DetailScreen(
    itemId: String,
    onPlay: (magnetUri: String, fileIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = "Detalle del ítem: $itemId",
        modifier = modifier
            .fillMaxSize()
            .align(Alignment.Center)
    )
}
