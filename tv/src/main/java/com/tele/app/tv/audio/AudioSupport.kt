package com.tele.app.tv.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import androidx.media3.common.C

/**
 * Qué formatos de audio sabe manejar este equipo.
 *
 * Interesa distinguir tres caminos distintos, porque no son lo mismo:
 *
 *  1. **Decodificador de plataforma** (`MediaCodecList`): el equipo tiene un
 *     decoder y ExoPlayer convierte a PCM. Es el camino "seguro".
 *  2. **Passthrough / bitstream** (`AudioCapabilities`): el equipo puede sacar el
 *     audio comprimido por HDMI tal cual y lo decodifica el televisor o el
 *     receptor AV. Es lo que queremos aprovechar en un TV box.
 *  3. **Offload** (`AudioManager.isOffloadedPlaybackSupported`, API 29+): el
 *     decodifica en el DSP de bajo consumo. Típico en chips Amlogic.
 *
 * Ojo: el sink de Media3 por defecto anuncia solo PCM, así que el passthrough
 * hay que cablearlo a mano — es lo que hace [AudioAwareRenderersFactory].
 */
data class AudioCodecSupport(
    val name: String,
    val mimeType: String,
    val encoding: Int,
    val passthrough: Boolean,
    val hasDecoder: Boolean,
    val offload: Boolean
) {
    val isUsable: Boolean get() = passthrough || hasDecoder || offload
}

data class AudioSupportReport(
    val maxChannelCount: Int,
    val codecs: List<AudioCodecSupport>
) {
    fun byMimeType(mimeType: String?): AudioCodecSupport? =
        mimeType?.let { mime -> codecs.firstOrNull { it.mimeType.equals(mime, ignoreCase = true) } }

    /** Línea compacta y honesta para el overlay del reproductor. */
    fun summary(): String = codecs.joinToString(" · ") { codec ->
        "${codec.name}: " + when {
            codec.passthrough -> "passthrough"
            codec.hasDecoder -> "decoder"
            codec.offload -> "offload"
            else -> "no"
        }
    }

    fun log(tag: String) {
        Log.i(tag, "canales max=$maxChannelCount")
        codecs.forEach { codec ->
            Log.i(
                tag,
                "${codec.name} (${codec.mimeType}): passthrough=${codec.passthrough} " +
                    "decoder=${codec.hasDecoder} offload=${codec.offload}"
            )
        }
    }
}

fun readAudioSupport(context: Context): AudioSupportReport {
    val capabilities = try {
        androidx.media3.exoplayer.audio.AudioCapabilities.getCapabilities(context)
    } catch (error: Exception) {
        Log.w(TAG, "No se pudieron leer las capacidades de audio: ${error.message}")
        null
    }

    val decoderTypes = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .codecInfos
            .asSequence()
            .filter { !it.isEncoder }
            .flatMap { it.supportedTypes.asSequence() }
            .map { it.lowercase() }
            .toSet()
    } catch (error: Exception) {
        Log.w(TAG, "No se pudo listar los codecs: ${error.message}")
        emptySet()
    }

    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    val codecs = CANDIDATES.map { candidate ->
        AudioCodecSupport(
            name = candidate.name,
            mimeType = candidate.mimeType,
            encoding = candidate.encoding,
            passthrough = capabilities?.supportsEncoding(candidate.encoding) ?: false,
            hasDecoder = decoderTypes.contains(candidate.mimeType),
            offload = supportsOffload(audioManager, candidate.encoding)
        )
    }

    return AudioSupportReport(
        maxChannelCount = capabilities?.maxChannelCount ?: 0,
        codecs = codecs
    )
}

private fun supportsOffload(audioManager: AudioManager?, encoding: Int): Boolean {
    if (audioManager == null) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

    val audioFormat = AudioFormat.Builder()
        .setEncoding(encoding)
        .setSampleRate(OFFLOAD_SAMPLE_RATE)
        .setChannelMask(AudioFormat.CHANNEL_OUT_5POINT1)
        .build()

    val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
        .build()

    return try {
        audioManager.isOffloadedPlaybackSupported(audioFormat, attributes)
    } catch (error: Exception) {
        false
    }
}

private data class Candidate(val name: String, val mimeType: String, val encoding: Int)

private const val TAG = "TeleTvAudio"
private const val OFFLOAD_SAMPLE_RATE = 48_000

private val CANDIDATES = listOf(
    Candidate("AC3", "audio/ac3", C.ENCODING_AC3),
    Candidate("E-AC3", "audio/eac3", C.ENCODING_E_AC3),
    Candidate("DTS", "audio/vnd.dts", C.ENCODING_DTS),
    Candidate("DTS-HD", "audio/vnd.dts.hd", C.ENCODING_DTS_HD),
    Candidate("AAC", "audio/mp4a-latm", C.ENCODING_AAC_LC)
)
