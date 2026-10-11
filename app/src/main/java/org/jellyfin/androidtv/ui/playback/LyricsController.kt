package org.jellyfin.androidtv.ui.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Loads song lyrics from the server (Jellyfin 10.9+, /Audio/{id}/Lyrics) and returns the line for
 * the current playback position. Only time-synced lyrics are shown.
 */
class LyricsController(private val scope: CoroutineScope) : KoinComponent {
	private val api by inject<ApiClient>()

	private data class Line(val startMs: Long, val text: String)

	private var itemId: String? = null
	private var lines: List<Line> = emptyList()
	private var job: Job? = null

	fun load(newItemId: String?) {
		if (newItemId == itemId) return
		itemId = newItemId
		lines = emptyList()
		job?.cancel()
		if (newItemId.isNullOrBlank()) return

		job = scope.launch {
			val loaded = withContext(Dispatchers.IO) {
				try {
					val json = api.get<JsonObject>("/Audio/{itemId}/Lyrics", mapOf("itemId" to newItemId)).content
					json["Lyrics"]?.jsonArray.orEmpty().mapNotNull { element ->
						val line = element.jsonObject
						val start = line["Start"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
						val text = line["Text"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
						Line(start / 10_000, text)
					}.sortedBy { it.startMs }
				} catch (err: Exception) {
					// 404 when the song has no lyrics, older servers don't have the endpoint at all
					Timber.d("No lyrics for %s: %s", newItemId, err.message)
					emptyList()
				}
			}
			if (itemId == newItemId) lines = loaded
		}
	}

	val hasLyrics: Boolean get() = lines.isNotEmpty()

	/** Lyric line at the given position, or null when there are no (synced) lyrics. */
	fun lineAt(positionMs: Long): String? {
		val current = lines
		if (current.isEmpty()) return null
		var result: Line? = null
		for (line in current) {
			if (line.startMs <= positionMs) result = line else break
		}
		return result?.text ?: ""
	}

	/** Line following the current one, for a small "up next" preview. */
	fun nextLineAt(positionMs: Long): String? = lines.firstOrNull { it.startMs > positionMs }?.text
}
