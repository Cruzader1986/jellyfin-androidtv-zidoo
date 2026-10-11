package org.jellyfin.androidtv.telemetry

import android.content.Context
import android.os.Build
import org.jellyfin.androidtv.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash in a local file so it can be read from the settings without ADB or a crash
 * report server.
 */
object CrashLog {
	private const val FILE_NAME = "last_crash.txt"

	private fun file(context: Context) = File(context.filesDir, FILE_NAME)

	fun install(context: Context) {
		val appContext = context.applicationContext
		val previous = Thread.getDefaultUncaughtExceptionHandler()
		Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
			runCatching { write(appContext, thread, throwable) }
			previous?.uncaughtException(thread, throwable)
		}
	}

	private fun write(context: Context, thread: Thread, throwable: Throwable) {
		val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
		val text = buildString {
			appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())}")
			appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
			appendLine("Device: ${Build.BRAND} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
			appendLine("Thread: ${thread.name}")
			appendLine()
			append(stack)
		}
		file(context).writeText(text.take(64 * 1024))
	}

	fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

	fun clear(context: Context) {
		file(context).delete()
	}
}
