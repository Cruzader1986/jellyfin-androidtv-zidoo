package org.jellyfin.androidtv.ui.itemdetail

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.get
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Finds the collection (box set) a movie belongs to. Box sets don't show up as a movie's parent,
 * so match the movie's TMDb collection id against the box sets first, then fall back to looking
 * inside the (few) manually created collections.
 */
object CollectionFinder : KoinComponent {
	private const val MAX_COLLECTIONS_TO_SCAN = 40

	private val api by inject<ApiClient>()
	private val userRepository by inject<UserRepository>()

	fun interface Callback {
		fun onFound(collectionId: String, collectionName: String)
	}

	private data class BoxSet(val id: String, val name: String, val tmdbId: String?)

	@JvmStatic
	fun find(activity: FragmentActivity, itemId: String?, providerIds: Map<String, String>?, callback: Callback) {
		if (itemId.isNullOrBlank()) return
		val userId = userRepository.currentUser.value?.id?.toString() ?: return
		val tmdbCollection = providerIds?.entries
			?.firstOrNull { it.key.equals("TmdbCollection", ignoreCase = true) }
			?.value?.takeIf { it.isNotBlank() }

		activity.lifecycleScope.launch {
			val found = withContext(Dispatchers.IO) {
				try {
					val boxSets = loadBoxSets(userId)
					boxSets.firstOrNull { tmdbCollection != null && it.tmdbId == tmdbCollection }
						?: scanCollections(userId, itemId, boxSets.filter { it.tmdbId == null || tmdbCollection == null })
				} catch (err: Exception) {
					Timber.w(err, "Unable to find collection for %s", itemId)
					null
				}
			}
			if (found != null && !activity.isFinishing && !activity.isDestroyed) callback.onFound(found.id, found.name)
		}
	}

	private suspend fun loadBoxSets(userId: String): List<BoxSet> =
		api.get<JsonObject>("/Items", queryParameters = mapOf(
			"userId" to userId,
			"includeItemTypes" to "BoxSet",
			"recursive" to true,
			"fields" to "ProviderIds",
		)).content["Items"]?.jsonArray.orEmpty().mapNotNull { element ->
			val json = element.jsonObject
			val id = json["Id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
			val tmdb = json["ProviderIds"]?.jsonObject?.entries
				?.firstOrNull { it.key.equals("Tmdb", ignoreCase = true) }
				?.value?.jsonPrimitive?.contentOrNull
			BoxSet(id, json["Name"]?.jsonPrimitive?.contentOrNull ?: "", tmdb)
		}

	private suspend fun scanCollections(userId: String, itemId: String, candidates: List<BoxSet>): BoxSet? {
		if (candidates.isEmpty() || candidates.size > MAX_COLLECTIONS_TO_SCAN) return null
		val normalizedId = itemId.replace("-", "").lowercase()
		return coroutineScope {
			candidates.map { boxSet ->
				async {
					val children = runCatching {
						api.get<JsonObject>("/Items", queryParameters = mapOf(
							"userId" to userId,
							"parentId" to boxSet.id,
						)).content["Items"]?.jsonArray.orEmpty()
					}.getOrDefault(emptyList())
					boxSet.takeIf {
						children.any { child ->
							child.jsonObject["Id"]?.jsonPrimitive?.contentOrNull?.replace("-", "")?.lowercase() == normalizedId
						}
					}
				}
			}.awaitAll().firstOrNull { it != null }
		}
	}
}
