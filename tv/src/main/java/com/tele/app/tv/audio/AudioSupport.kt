package com.tele.app.tv.audio

import android.content.Context
import android.media.MediaCodecList
import android.util.Log
import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioCapabilities

/**
 * Qué formatos de audio sabe manejar este equipo, en los dos caminos que la app
 * realmente usa:
 *
 *  1. **Passthrough / bitstream**: el equipo puede sacar el audio comprimido por
 *     HDMI tal cual y lo decodifica el televisor o el receptor AV
 *     (`AudioCapabilities`). Es lo que aprovecha un TV box con AC3.
 *  2. **Decodificador de plataforma** (`MediaCodecList`): hay decoder local y
 *     ExoPlayer convierte a PCM.
 *
 * Ojo: el sink de Media3 por defecto anuncia **solo PCM**, así que el passthrough
 * hay que cablearlo a mano; de eso se encarga [AudioAwareRenderersFactory].
 */
data class AudioCodecSupport(
    val name: String,
    val mimeType: String,
    val passthrough: Boolean,
    val hasDecoder: Boolean
) {
    val isUsable: Boolean get() = passthrough || hasDecoder

    val route: String
        get() = when {
            passthrough -> "passthrough"
            hasDecoder -> "decoder"
            else -> "no"
        }
}

data class AudioSupportReport(
    val maxChannelCount: Int,
    val codecs: List<AudioCodecSupport>
) {
    fun byMimeType(mimeType: String?): AudioCodecSupport? =
        mimeType?.let { mime -> codecs.firstOrNull { it.mimeType.equals(mime, ignoreCase = true) } }

    /** Línea compacta para el overlay del reproductor. */
    fun summary(): String =
        codecs.joinToString(" · ") { codec -> "${codec.name}: ${codec.route}" }

    fun log(tag: String) {
        Log.i(tag, "canales max=$maxChannelCount")
        codecs.forEach { codec ->
            Log.i(
                tag,
                "${codec.name} (${codec.mimeType}): passthrough=${codec.passthrough} " +
                    "decoder=${codec.hasDecoder}"
            )
        }
    }
}

fun readAudioSupport(context: Context): AudioSupportReport {
    val capabilities = try {
        AudioCapabilities.getCapabilities(context)
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

    return AudioSupportReport(
        maxChannelCount = capabilities?.maxChannelCount ?: 0,
        codecs = CANDIDATES.map { candidate ->
            AudioCodecSupport(
                name = candidate.name,
                mimeType = candidate.mimeType,
                passthrough = capabilities?.supportsEncoding(candidate.encoding) ?: false,
                hasDecoder = decoderTypes.contains(candidate.mimeType)
            )
        }
    )
}

private data class Candidate(val name: String, val mimeType: String, val encoding: Int)

private const val TAG = "TeleTvAudio"

private val CANDIDATES = listOf(
    Candidate("AC3", "audio/ac3", C.ENCODING_AC3),
    Candidate("E-AC3", "audio/eac3", C.ENCODING_E_AC3),
    Candidate("DTS", "audio/vnd.dts", C.ENCODING_DTS),
    Candidate("DTS-HD", "audio/vnd.dts.hd", C.ENCODING_DTS_HD),
    Candidate("AAC", "audio/mp4a-latm", C.ENCODING_AAC_LC)
)
