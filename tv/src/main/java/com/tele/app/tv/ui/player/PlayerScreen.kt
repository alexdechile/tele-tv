package com.tele.app.tv.ui.player

import androidx.compose.runtime.Composable
import androidx.tv.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment

@Composable
fun PlayerScreen(
    magnetUri: String,
    fileIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = "Reproductor (magnet: $magnetUri, archivo: $fileIndex)",
        modifier = modifier
            .fillMaxSize()
            .align(Alignment.Center)
    )
}
