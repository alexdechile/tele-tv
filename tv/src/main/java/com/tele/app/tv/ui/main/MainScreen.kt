package com.tele.app.tv.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import com.tele.app.tv.domain.model.AddonItem

@Composable
fun MainScreen(
    items: List<AddonItem> = emptyList(),
    onItemSelected: (AddonItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = "Catálogo de addons (${items.size} ítems)",
        modifier = modifier
            .fillMaxSize()
            .align(Alignment.Center)
    )
}
