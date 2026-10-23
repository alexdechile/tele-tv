package com.tele.app.tv.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text

@Composable
fun DetailScreen(
    itemId: String,
    onPlay: (magnetUri: String, fileIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Text(text = "Detalle del ítem: $itemId")
}
