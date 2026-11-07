package com.tele.app.tv.ui.downloads

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.tele.app.tv.data.local.DownloadScanner
import com.tele.app.tv.data.local.DownloadedFile
import com.tele.app.tv.ui.components.TeleCard
import com.tele.app.tv.ui.theme.PastelTheme
import kotlinx.coroutines.delay

/**
 * Explorador de descargas guardadas en el TV Box.
 * Permite listar, seleccionar, reproducir y borrar permanentemente archivos.
 */
@Composable
fun DownloadsScreen(
    onPlay: (DownloadedFile) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloads = remember { mutableStateListOf<DownloadedFile>() }
    var fileToDelete by remember { mutableStateOf<DownloadedFile?>(null) }
    var isLoaded by remember { mutableStateOf(false) }

    val refreshList = {
        downloads.clear()
        downloads.addAll(DownloadScanner.scan(context))
        isLoaded = true
    }

    LaunchedEffect(Unit) {
        refreshList()
    }

    BackHandler {
        if (fileToDelete != null) {
            fileToDelete = null
        } else {
            onBack()
        }
    }

    val totalBytes = remember(downloads.size) { downloads.sumOf { it.sizeBytes } }
    val formattedTotal = remember(totalBytes) { DownloadScanner.formatFileSize(totalBytes) }

    Surface(
        modifier = modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PastelTheme.AppAmbientGradient)
                .padding(horizontal = 32.dp, vertical = 20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Barra superior
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PillButton(text = "← Volver", onClick = onBack)
                        Column {
                            Text(
                                text = "📁 Mis Descargas",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = PastelTheme.LavenderLight
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (downloads.isEmpty()) {
                                    "Sin descargas almacenadas en este equipo"
                                } else {
                                    "${downloads.size} ${if (downloads.size == 1) "archivo" else "archivos"} guardados · $formattedTotal ocupados"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = PastelTheme.SkyLight
                            )
                        }
                    }

                    if (downloads.isNotEmpty()) {
                        PillButton(text = "Refrescar", onClick = { refreshList() })
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (isLoaded && downloads.isEmpty()) {
                    // Estado vacío
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xF0101726))
                                .border(1.5.dp, PastelTheme.SurfaceBorder, RoundedCornerShape(20.dp))
                                .padding(36.dp)
                        ) {
                            Text(
                                text = "No hay descargas conservadas",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = PastelTheme.LavenderLight
                            )
                            Text(
                                text = "Cuando reproduzcas cualquier película o serie, pulsa el botón '💾 Conservar archivo'\npara guardarla en la TV y poder verla cuando quieras sin internet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFF1F5F9),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            PillButton(text = "← Volver al catálogo", onClick = onBack)
                        }
                    }
                } else {
                    // Lista de descargas
                    val firstFocus = remember { FocusRequester() }
                    LaunchedEffect(downloads.size) {
                        if (downloads.isNotEmpty()) {
                            repeat(3) {
                                if (runCatching { firstFocus.requestFocus() }.isSuccess) return@LaunchedEffect
                                delay(50)
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(downloads, key = { _, item -> item.file.absolutePath }) { index, item ->
                            DownloadRow(
                                item = item,
                                focusRequester = if (index == 0) firstFocus else null,
                                onPlay = { onPlay(item) },
                                onDelete = { fileToDelete = item }
                            )
                        }
                    }
                }
            }

            // Diálogo de confirmación para borrar permanentemente
            fileToDelete?.let { target ->
                DeleteDialog(
                    item = target,
                    onConfirm = {
                        val deleted = DownloadScanner.deleteDownload(target)
                        fileToDelete = null
                        refreshList()
                        Toast.makeText(
                            context,
                            if (deleted) "Archivo eliminado permanentemente" else "No se pudo borrar el archivo",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onDismiss = { fileToDelete = null }
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadedFile,
    focusRequester: FocusRequester? = null,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF0131B2E))
            .border(1.dp, Color(0x33C7B9FF), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = "🎬", style = MaterialTheme.typography.titleLarge)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.cleanTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${item.formattedSize} · Guardado el ${item.formattedDate}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = PastelTheme.MintLight
                    )
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = PastelTheme.SkyLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillButton(
                    text = "▶ Reproducir",
                    focusRequester = focusRequester,
                    colors = CardDefaults.colors(
                        containerColor = Color(0xF0182238),
                        focusedContainerColor = PastelTheme.Mint,
                        contentColor = Color.White,
                        focusedContentColor = PastelTheme.MintDark
                    ),
                    border = CardDefaults.border(
                        border = Border(BorderStroke(1.dp, Color(0x44A7F3D0))),
                        focusedBorder = Border(BorderStroke(2.5.dp, Color.White))
                    ),
                    onClick = onPlay
                )

                PillButton(
                    text = "🗑 Borrar",
                    colors = CardDefaults.colors(
                        containerColor = Color(0xF0182238),
                        focusedContainerColor = PastelTheme.Coral,
                        contentColor = PastelTheme.Coral,
                        focusedContentColor = PastelTheme.CoralDark
                    ),
                    border = CardDefaults.border(
                        border = Border(BorderStroke(1.dp, Color(0x44FECDD3))),
                        focusedBorder = Border(BorderStroke(2.5.dp, Color.White))
                    ),
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    colors: CardColors = CardDefaults.colors(
        containerColor = Color(0xF0182238),
        focusedContainerColor = PastelTheme.Lavender,
        contentColor = Color.White,
        focusedContentColor = PastelTheme.LavenderDark
    ),
    border: CardBorder = CardDefaults.border(
        border = Border(BorderStroke(1.dp, Color(0x33C7B9FF))),
        focusedBorder = Border(BorderStroke(2.5.dp, Color.White))
    ),
    onClick: () -> Unit
) {
    TeleCard(
        onClick = onClick,
        colors = colors,
        border = border,
        modifier = modifier
            .height(44.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DeleteDialog(
    item: DownloadedFile,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(4) {
            if (runCatching { cancelFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD90B0F19))
            .padding(horizontal = 48.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF0141A29))
                .border(1.5.dp, PastelTheme.Coral, RoundedCornerShape(20.dp))
                .padding(28.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "¿Borrar permanentemente?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = PastelTheme.Coral
                )
                Text(
                    text = "Se eliminará el archivo \"${item.cleanTitle}\" de la TV y se liberarán ${item.formattedSize} de almacenamiento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                ) {
                    PillButton(
                        text = "Cancelar",
                        focusRequester = cancelFocus,
                        onClick = onDismiss
                    )
                    PillButton(
                        text = "🗑 Sí, borrar archivo",
                        colors = CardDefaults.colors(
                            containerColor = Color(0xF02A121E),
                            focusedContainerColor = PastelTheme.Coral,
                            contentColor = PastelTheme.Coral,
                            focusedContentColor = PastelTheme.CoralDark
                        ),
                        border = CardDefaults.border(
                            border = Border(BorderStroke(1.5.dp, PastelTheme.Coral)),
                            focusedBorder = Border(BorderStroke(2.5.dp, Color.White))
                        ),
                        onClick = onConfirm
                    )
                }
            }
        }
    }
}
