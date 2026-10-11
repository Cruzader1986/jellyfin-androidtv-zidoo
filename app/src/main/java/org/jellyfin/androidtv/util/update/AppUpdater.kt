package org.jellyfin.androidtv.util.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.androidtv.BuildConfig
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks the fork's GitHub releases for a newer signed APK, downloads it and hands it to the
 * system package installer.
 */
object AppUpdater {
	const val REPOSITORY = "Cruzader1986/jellyfin-androidtv-zidoo"
	private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"

	data class Release(
		val version: String,
		val name: String,
		val notes: String,
		val apkUrl: String,
		val apkSize: Long,
	)

	private val json = Json { ignoreUnknownKeys = true }

	/** Version of the running app without the "-zidoo_edition" style suffix. */
	val currentVersion: String
		get() = cleanVersion(BuildConfig.VERSION_NAME)

	fun cleanVersion(version: String?): String = version.orEmpty()
		.trim()
		.removePrefix("v")
		.removePrefix("V")
		.substringBefore('-')
		.substringBefore('+')

	/** Compare dotted numeric versions, true when [candidate] is newer than [current]. */
	fun isNewer(candidate: String, current: String): Boolean {
		val a = cleanVersion(candidate).split('.').map { it.toIntOrNull() ?: 0 }
		val b = cleanVersion(current).split('.').map { it.toIntOrNull() ?: 0 }
		for (i in 0 until maxOf(a.size, b.size)) {
			val x = a.getOrElse(i) { 0 }
			val y = b.getOrElse(i) { 0 }
			if (x != y) return x > y
		}
		return false
	}

	/** Fetch the latest published release, or null when there is none / it has no APK. */
	suspend fun fetchLatestRelease(): Release? = withContext(Dispatchers.IO) {
		val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
		try {
			connection.connectTimeout = 10_000
			connection.readTimeout = 15_000
			connection.setRequestProperty("Accept", "application/vnd.github+json")
			connection.setRequestProperty("User-Agent", "jellyfin-androidtv-zidoo/${BuildConfig.VERSION_NAME}")
			if (connection.responseCode != HttpURLConnection.HTTP_OK) {
				Timber.w("Update check failed with HTTP %d", connection.responseCode)
				return@withContext null
			}

			val body = connection.inputStream.bufferedReader().use { it.readText() }
			val release = json.parseToJsonElement(body).jsonObject
			val tag = release.string("tag_name") ?: return@withContext null
			val asset = release["assets"]?.jsonArray
				?.map { it.jsonObject }
				?.firstOrNull { it.string("name")?.endsWith(".apk", ignoreCase = true) == true }
				?: return@withContext null

			Release(
				version = cleanVersion(tag),
				name = release.string("name")?.takeIf { it.isNotBlank() } ?: tag,
				notes = release.string("body").orEmpty(),
				apkUrl = asset.string("browser_download_url") ?: return@withContext null,
				apkSize = asset["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: -1,
			)
		} catch (err: Exception) {
			Timber.w(err, "Update check failed")
			null
		} finally {
			connection.disconnect()
		}
	}

	private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull

	private fun updateDir(context: Context) = File(context.cacheDir, "updates").apply { mkdirs() }

	private fun openFollowingRedirects(start: URL): HttpURLConnection {
		var url = start
		for (attempt in 0..5) {
			val connection = url.openConnection() as HttpURLConnection
			connection.connectTimeout = 15_000
			connection.readTimeout = 30_000
			connection.instanceFollowRedirects = false
			connection.setRequestProperty("User-Agent", "jellyfin-androidtv-zidoo/${BuildConfig.VERSION_NAME}")
			val code = connection.responseCode
			if (code in 300..399) {
				val location = connection.getHeaderField("Location")
				connection.disconnect()
				if (location.isNullOrBlank()) break
				url = URL(url, location)
				continue
			}
			if (code != HttpURLConnection.HTTP_OK) {
				connection.disconnect()
				throw IllegalStateException("Download failed with HTTP $code")
			}
			return connection
		}
		throw IllegalStateException("Download failed: too many redirects")
	}

	/** Download the APK into the cache, reporting progress 0..100 (or -1 when the size is unknown). */
	suspend fun download(context: Context, release: Release, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
		val dir = updateDir(context)
		dir.listFiles()?.forEach { it.delete() }
		val target = File(dir, "jellyfin-zidoo-${release.version}.apk")

		val connection = openFollowingRedirects(URL(release.apkUrl))

		try {
			val total = connection.contentLength.toLong().takeIf { it > 0 } ?: release.apkSize
			var lastProgress = -2
			connection.inputStream.use { input ->
				target.outputStream().use { output ->
					val buffer = ByteArray(64 * 1024)
					var done = 0L
					while (true) {
						val read = input.read(buffer)
						if (read < 0) break
						output.write(buffer, 0, read)
						done += read
						val progress = if (total > 0) (done * 100 / total).toInt() else -1
						if (progress != lastProgress) {
							lastProgress = progress
							withContext(Dispatchers.Main) { onProgress(progress) }
						}
					}
				}
			}
		} finally {
			connection.disconnect()
		}
		target
	}

	/**
	 * Android 8+ needs the "install unknown apps" permission for this app. Returns false (and opens
	 * the settings page when possible) when it is still missing.
	 */
	fun ensureInstallPermission(context: Context): Boolean {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
		if (context.packageManager.canRequestPackageInstalls()) return true

		try {
			context.startActivity(
				Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			)
		} catch (err: ActivityNotFoundException) {
			Timber.w(err, "No unknown sources settings screen")
		}
		return false
	}

	fun install(context: Context, apk: File) {
		val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
		} else {
			Uri.fromFile(apk)
		}

		@Suppress("DEPRECATION")
		val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
			.setDataAndType(uri, "application/vnd.android.package-archive")
			.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
			.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)

		try {
			context.startActivity(intent)
		} catch (err: ActivityNotFoundException) {
			context.startActivity(
				Intent(Intent.ACTION_VIEW)
					.setDataAndType(uri, "application/vnd.android.package-archive")
					.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
			)
		}
	}
}
