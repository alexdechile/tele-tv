package com.tele.app.tv.ui.player

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.media3.ui.PlayerView

/**
 * [PlayerView] que no compite por el toque ni por el foco.
 *
 * media3 deja este View `clickable` cuando hay controlador activo o un
 * `OnClickListener` puesto a mano; un View real dentro de un `AndroidView`
 * despacha el toque antes de que Compose haga su hit-test, así que en ese
 * estado se comería los clics de la barra de controles.
 *
 * Con `app:use_controller="false"` media3 1.3.1 ya no lo marca clickable, pero
 * dependíamos de ese detalle fino de la librería: si mañana alguien reactiva el
 * controlador nativo, los botones de Compose vuelven a morir en silencio. Acá
 * queda explícito y a prueba de regresiones.
 */
class TouchPassThroughPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr) {

    init {
        // El foco y los clics los maneja la barra de Compose.
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        setOnClickListener(null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    override fun onTrackballEvent(event: MotionEvent): Boolean = false

    override fun performClick(): Boolean = false
}
