package com.tele.app.tv.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.Text
import androidx.tv.material3.focus.focusRestorer
import androidx.tv.material3.focus.focusRequester
import com.tele.app.tv.domain.model.AddonItem

@Composable
fun MainScreen(
    items: List<AddonItem> = emptyList(),
    onItemSelected: (AddonItem) -> Unit,
    modifier: Modifier = Modifier
) {
    // TvLazyRow requiere androidx.tv.material3; se deja esqueleto listo para conectar
    // con datos reales del addon en el siguiente milestone.
    Card(
        modifier = modifier
            .fillMaxSize()
            .focusRequester { /* requester -> requestFocus() */ }
            .focusRestorer()
    ) {
        Text(
            text = "Catálogo de addons (${items.size} ítems)",
            modifier = Modifier.align(alignment = androidx.compose.ui.Alignment.Center)
        )
    }
}
