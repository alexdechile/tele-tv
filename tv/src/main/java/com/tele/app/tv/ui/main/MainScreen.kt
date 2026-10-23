package com.tele.app.tv.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import com.tele.app.tv.domain.model.AddonItem

@Composable
fun MainScreen(
    items: List<AddonItem> = emptyList(),
    onItemSelected: (AddonItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Text(text = "Catálogo de addons (${items.size} ítems)")
}
