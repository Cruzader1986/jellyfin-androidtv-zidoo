package org.jellyfin.androidtv.ui.playback

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.jellyfin.androidtv.preference.constant.SegmentSkipBehavior
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.get
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Loads Jellyfin media segments (Jellyfin 10.10+, GET /MediaSegments/{itemId}).
 * The bundled SDK (1.4.x) predates this endpoint, so the response is parsed manually.
 */
object MediaSegmentFetcher : KoinComponent {
	private val api by inject<ApiClient>()

	class Segment(
		@JvmField val type: String,
		@JvmField val startMs: Int,
		@JvmField val endMs: Int,
	) {
		override fun toString() = "$type ${startMs}ms-${endMs}ms"
	}

	@JvmStatic
	fun typesFor(behavior: SegmentSkipBehavior): List<String> = when (behavior) {
		SegmentSkipBehavior.OFF -> emptyList()
		SegmentSkipBehavior.INTRO -> listOf("Intro")
		SegmentSkipBehavior.INTRO_AND_CREDITS -> listOf("Intro", "Outro")
		SegmentSkipBehavior.ALL -> listOf("Intro", "Outro", "Recap", "Preview")
	}

	/**
	 * Blocking call, only use from a background thread (the Zidoo task handler thread).
	 * Returns an empty list on any error or when the server does not support media segments.
	 */
	@JvmStatic
	fun fetchBlocking(itemId: String, types: List<String>): List<Segment> {
		if (types.isEmpty()) return emptyList()
		return runCatching {
			runBlocking {
				val json = api.get<JsonObject>(
					"/MediaSegments/{itemId}",
					pathParameters = mapOf("itemId" to itemId),
					queryParameters = mapOf("includeSegmentTypes" to types),
				).content

				json["Items"]?.jsonArray.orEmpty().mapNotNull { element ->
					val obj = element.jsonObject
					val type = obj["Type"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
					val start = obj["StartTicks"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
					val end = obj["EndTicks"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
					if (end <= start) return@mapNotNull null
					Segment(type, (start / 10_000).toInt(), (end / 10_000).toInt())
				}.sortedBy { it.startMs }
			}
		}.onFailure {
			Timber.w(it, "Unable to load media segments for %s", itemId)
		}.getOrDefault(emptyList())
	}
}
