package com.tele.app.tv.ui.player

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.media3.ui.PlayerView

/**
 * [PlayerView] que NO se queda con los eventos de toque.
 *
 * media3 marca este View como `clickable` en su propio constructor (para
 * mostrar/ocultar su controlador nativo). Como es un View real dentro de un
 * `AndroidView`, se queda con el `ACTION_DOWN` antes de que Compose haga su
 * hit-test: la barra de controles de Compose se dibujaba encima, pero nunca
 * recibía un solo toque. Ese era el bug de "los botones no funcionan".
 *
 * Desactivar el controlador con `app:use_controller="false"` apaga el overlay
 * visual de media3, pero no desactiva el `clickable`, así que seguía tragándose
 * los eventos. Aquí se suelta el evento devolviendo `false`.
 */
class TouchPassThroughPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr) {

    init {
        // El foco de la barra lo maneja Compose; este View no debe competir por él
        // ni responder a clics propios.
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        setOnClickListener(null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    override fun onTrackballEvent(event: MotionEvent): Boolean = false

    override fun performClick(): Boolean = false
}
