package org.jellyfin.androidtv.ui.itemdetail

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.get
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.net.URLEncoder

/**
 * Plays an item's theme song (or its series' theme song) quietly while its details screen is open.
 * Moving between details screens that share the same theme (series -> season -> episode) keeps the
 * song playing instead of restarting it.
 */
object ThemeMusicPlayer : KoinComponent {
	private const val VOLUME = 0.35f
	private const val STOP_DELAY_MS = 800L

	private val api by inject<ApiClient>()
	private val userRepository by inject<UserRepository>()
	private val userPreferences by inject<UserPreferences>()
	private val mediaManager by inject<MediaManager>()

	private val scope = MainScope()
	private val handler = Handler(Looper.getMainLooper())
	private val stopRunnable = Runnable { stopNow() }

	private var player: MediaPlayer? = null
	private var currentSongId: String? = null
	private var lookup: Job? = null

	@JvmStatic
	fun play(itemId: String?) {
		handler.removeCallbacks(stopRunnable)
		if (itemId.isNullOrBlank() || !userPreferences[UserPreferences.themeMusicEnabled] || mediaManager.isPlayingAudio) {
			stopNow()
			return
		}

		lookup?.cancel()
		lookup = scope.launch {
			val songId = withContext(Dispatchers.IO) { findThemeSong(itemId) }
			if (songId == null) {
				stopNow()
				return@launch
			}
			if (songId == currentSongId && player != null) return@launch

			stopNow()
			start(songId)
		}
	}

	/** Called when a details screen is paused, the next details screen can keep the song going. */
	@JvmStatic
	fun stopSoon() {
		handler.removeCallbacks(stopRunnable)
		handler.postDelayed(stopRunnable, STOP_DELAY_MS)
	}

	@JvmStatic
	fun stopNow() {
		handler.removeCallbacks(stopRunnable)
		lookup?.cancel()
		lookup = null
		currentSongId = null
		player?.let {
			runCatching { it.stop() }
			it.release()
		}
		player = null
	}

	private suspend fun findThemeSong(itemId: String): String? = try {
		val userId = userRepository.currentUser.value?.id?.toString()
		val result = api.get<JsonObject>(
			"/Items/{itemId}/ThemeSongs",
			mapOf("itemId" to itemId),
			mapOf("userId" to userId, "inheritFromParent" to true),
		).content
		result["Items"]?.jsonArray?.firstOrNull()?.jsonObject?.get("Id")?.jsonPrimitive?.contentOrNull
	} catch (err: Exception) {
		Timber.d("No theme song for %s: %s", itemId, err.message)
		null
	}

	private fun start(songId: String) {
		val baseUrl = api.baseUrl?.trimEnd('/') ?: return
		val token = api.accessToken ?: return
		val url = "$baseUrl/Audio/$songId/stream?static=true&ApiKey=${URLEncoder.encode(token, "UTF-8")}"

		val mediaPlayer = MediaPlayer()
		player = mediaPlayer
		currentSongId = songId
		try {
			mediaPlayer.setAudioAttributes(
				AudioAttributes.Builder()
					.setUsage(AudioAttributes.USAGE_MEDIA)
					.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
					.build()
			)
			mediaPlayer.setDataSource(url)
			mediaPlayer.setVolume(VOLUME, VOLUME)
			mediaPlayer.setOnPreparedListener { if (player === it) it.start() }
			mediaPlayer.setOnCompletionListener { if (player === it) stopNow() }
			mediaPlayer.setOnErrorListener { mp, what, extra ->
				Timber.w("Theme song playback error %d/%d", what, extra)
				if (player === mp) stopNow()
				true
			}
			mediaPlayer.prepareAsync()
		} catch (err: Exception) {
			Timber.w(err, "Unable to play theme song")
			stopNow()
		}
	}
}
