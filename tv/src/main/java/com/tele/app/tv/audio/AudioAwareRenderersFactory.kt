package com.tele.app.tv.audio

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Renderers de ExoPlayer con las capacidades de audio **reales** del equipo.
 *
 * Por qué existe: `DefaultAudioSink.Builder(context)` arranca con
 * `AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES`, que solo anuncia PCM 16 bits.
 * Con eso, ExoPlayer nunca ofrece passthrough: intenta decodificar todo por
 * MediaCodec y, en un archivo AC3/DTS sin decoder de plataforma, el resultado es
 * video con **silencio**.
 *
 * Al inyectar `AudioCapabilities.getCapabilities(context)` el sink sí anuncia
 * AC3/E-AC3/DTS cuando el televisor o el receptor los aceptan por HDMI, y
 * ExoPlayer prefiere ese camino (bitstream) antes que decodificar localmente.
 */
class AudioAwareRenderersFactory(
    context: Context,
    private val audioCapabilities: AudioCapabilities
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setAudioCapabilities(audioCapabilities)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .build()
}
