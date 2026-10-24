package com.tele.app.tv.crash

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Guarda el último crash para que se pueda diagnosticar sin adb.
 *
 * Escribe el stack trace en `cacheDir/last-crash.txt` y en logcat con la
 * etiqueta [TAG]. En el siguiente arranque la app puede mostrar ese texto, así
 * una foto de pantalla basta para reportar el problema.
 */
object CrashReporter {

    const val TAG = "TeleTvCrash"
    private const val FILE_NAME = "last-crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(appContext, thread, error) }
            Log.e(TAG, "Crash en hilo ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = try {
        val file = file(context)
        if (file.exists()) file.readText().takeIf { it.isNotBlank() } else null
    } catch (error: Exception) {
        null
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val text = buildString {
            appendLine("Tele TV — crash")
            appendLine("fecha: $stamp")
            appendLine("hilo: ${thread.name}")
            appendLine("error: ${error.javaClass.name}: ${error.message}")
            appendLine()
            appendLine(Log.getStackTraceString(error))
        }
        file(context).writeText(text)
    }

    private fun file(context: Context) = File(context.cacheDir, FILE_NAME)
}
