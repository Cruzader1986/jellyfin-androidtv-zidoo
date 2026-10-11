package org.jellyfin.androidtv.ui.itemdetail

import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.get
import org.jellyfin.sdk.api.client.extensions.post
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Add an item to one of the user's playlists or collections, or create a new one with it.
 */
object AddToPlaylistDialog : KoinComponent {
	private val api by inject<ApiClient>()
	private val userRepository by inject<UserRepository>()

	private enum class Target(val itemType: String, val title: Int, val newLabel: Int, val added: Int) {
		PLAYLIST("Playlist", R.string.lbl_add_to_playlist, R.string.lbl_new_playlist, R.string.msg_added_to_playlist),
		COLLECTION("BoxSet", R.string.lbl_add_to_collection, R.string.lbl_new_collection, R.string.msg_added_to_collection),
	}

	private data class Entry(val id: String, val name: String)

	@JvmStatic
	fun show(activity: FragmentActivity, itemId: String?) = open(activity, itemId, Target.PLAYLIST)

	@JvmStatic
	fun showCollections(activity: FragmentActivity, itemId: String?) = open(activity, itemId, Target.COLLECTION)

	private fun userId() = userRepository.currentUser.value?.id?.toString()

	private fun open(activity: FragmentActivity, itemId: String?, target: Target) {
		if (itemId.isNullOrBlank()) return
		val userId = userId() ?: return

		activity.lifecycleScope.launch {
			val entries = try {
				withContext(Dispatchers.IO) {
					api.get<JsonObject>("/Items", queryParameters = mapOf(
						"userId" to userId,
						"includeItemTypes" to target.itemType,
						"recursive" to true,
						"sortBy" to "SortName",
					)).content["Items"]?.jsonArray.orEmpty().mapNotNull { element ->
						val json = element.jsonObject
						val id = json["Id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
						Entry(id, json["Name"]?.jsonPrimitive?.contentOrNull ?: id)
					}
				}
			} catch (err: Exception) {
				Timber.w(err, "Unable to load %s list", target.itemType)
				emptyList()
			}
			if (activity.isFinishing || activity.isDestroyed) return@launch

			val labels = listOf(activity.getString(target.newLabel)) + entries.map { it.name }
			AlertDialog.Builder(activity)
				.setTitle(target.title)
				.setItems(labels.toTypedArray()) { _, which ->
					if (which == 0) askName(activity, itemId, target)
					else addTo(activity, itemId, target, entries[which - 1])
				}
				.show()
		}
	}

	private fun askName(activity: FragmentActivity, itemId: String, target: Target) {
		val padding = (24 * activity.resources.displayMetrics.density).toInt()
		val input = EditText(activity).apply {
			inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
			isSingleLine = true
		}
		val container = FrameLayout(activity).apply {
			setPadding(padding, padding / 2, padding, 0)
			addView(input)
		}
		AlertDialog.Builder(activity)
			.setTitle(target.newLabel)
			.setView(container)
			.setPositiveButton(android.R.string.ok) { _, _ ->
				val name = input.text?.toString()?.trim().orEmpty()
				if (name.isNotEmpty()) create(activity, itemId, target, name)
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
		input.requestFocus()
	}

	private fun create(activity: FragmentActivity, itemId: String, target: Target, name: String) = run(activity, target) {
		when (target) {
			Target.PLAYLIST -> api.post<JsonObject>("/Playlists", requestBody = buildJsonObject {
				put("Name", name)
				put("Ids", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(itemId)) })
				userId()?.let { put("UserId", it) }
			})
			Target.COLLECTION -> api.post<JsonObject>("/Collections", queryParameters = mapOf("name" to name, "ids" to itemId))
		}
	}

	private fun addTo(activity: FragmentActivity, itemId: String, target: Target, entry: Entry) = run(activity, target) {
		when (target) {
			Target.PLAYLIST -> api.post<Unit>(
				"/Playlists/{playlistId}/Items",
				mapOf("playlistId" to entry.id),
				mapOf("ids" to itemId, "userId" to userId()),
			)
			Target.COLLECTION -> api.post<Unit>(
				"/Collections/{collectionId}/Items",
				mapOf("collectionId" to entry.id),
				mapOf("ids" to itemId),
			)
		}
	}

	private fun run(activity: FragmentActivity, target: Target, request: suspend () -> Unit) {
		activity.lifecycleScope.launch {
			val message = try {
				withContext(Dispatchers.IO) { request() }
				target.added
			} catch (err: InvalidStatusException) {
				Timber.w(err, "Unable to update %s", target.itemType)
				if (err.status == 401 || err.status == 403) R.string.msg_no_permission else R.string.msg_update_failed
			} catch (err: Exception) {
				Timber.w(err, "Unable to update %s", target.itemType)
				R.string.msg_update_failed
			}
			if (!activity.isFinishing) Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
		}
	}
}
