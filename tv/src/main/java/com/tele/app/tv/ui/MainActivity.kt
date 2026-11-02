package com.tele.app.tv.ui

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tele.app.tv.crash.CrashReporter
import com.tele.app.tv.ui.components.TeleCard
import com.tele.app.tv.ui.navigation.AppNavigator

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.install(applicationContext)

        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)

        if (isTelevision()) {
            // En la TV no hay barra de navegación que valga: pantalla completa inmersiva.
            // (Elimina la banda gris del título nativo y la barra de estado.)
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            WindowCompat.setDecorFitsSystemWindows(window, false)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            // Tablet/telefono: el tema es `Fullscreen`, así que hay que devolver las barras
            // explícitamente. Sin barra de navegación no hay "atrás" visible y la app deja
            // de ser usable al toque; además el contenido tiene que respetar los insets.
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            WindowCompat.setDecorFitsSystemWindows(window, true)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }

        // Si la ejecución anterior se cayó, mostramos el error en pantalla:
        // así un problema se puede reportar con una foto, sin adb.
        val previousCrash = CrashReporter.read(applicationContext)

        setContent {
            var showCrash by remember { mutableStateOf(previousCrash != null) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                if (showCrash && previousCrash != null) {
                    CrashScreen(
                        trace = previousCrash,
                        onContinue = {
                            CrashReporter.clear(applicationContext)
                            showCrash = false
                        }
                    )
                } else {
                    AppNavigator()
                }
            }
        }
    }

    /**
     * ¿Corre en Android TV?
     *
     * El KM6 declara `leanback` y arranca con `UI_MODE_TYPE_TELEVISION`; la Lenovo
     * YT-X705F, ninguno de los dos. Se decide una sola vez en `onCreate`, así que la
     * tablet no hereda la pantalla inmersiva que la TV necesita.
     */
    private fun isTelevision(): Boolean {
        val byUiMode = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
        val byFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        return byUiMode || byFeature
    }
}

@Composable
private fun CrashScreen(trace: String, onContinue: () -> Unit) {
    val lines = remember(trace) { trace.lines() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "La ejecución anterior se cerró",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Copia o fotografía este detalle para diagnosticarlo:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(lines) { line ->
                Text(
                    text = line.ifBlank { " " },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        TeleCard(onClick = onContinue, modifier = Modifier.width(260.dp).height(64.dp)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Continuar a Tele TV", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
