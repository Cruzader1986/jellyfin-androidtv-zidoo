package org.jellyfin.androidtv.ui.itemdetail

import android.app.AlertDialog
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.LanguagesSubtitle
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.Locale

/**
 * Search and download subtitles through the server's subtitle providers (e.g. the OpenSubtitles
 * plugin). The server stores the file next to the media, so the Zidoo player picks it up as an
 * external subtitle.
 */
object SubtitleSearchDialog : KoinComponent {
	private val api by inject<ApiClient>()
	private val userPreferences by inject<UserPreferences>()

	private data class Result(val id: String, val label: String)

	@JvmStatic
	fun show(activity: FragmentActivity, itemId: String?) {
		if (itemId.isNullOrBlank()) return

		val preferred = userPreferences[UserPreferences.subtitleLanguage]
		val languages = linkedSetOf<String>()
		if (preferred != LanguagesSubtitle.AUTO) iso3(preferred.lang)?.let { languages += it }
		iso3(Locale.getDefault().language)?.let { languages += it }
		languages += "eng"

		val options = languages.map { code -> code to (Locale(code).displayLanguage.takeIf { it.isNotBlank() && it != code } ?: code) }
		AlertDialog.Builder(activity)
			.setTitle(R.string.lbl_subtitle_language_pick)
			.setItems(options.map { it.second }.toTypedArray()) { _, which -> search(activity, itemId, options[which].first) }
			.show()
	}

	private fun iso3(code: String?): String? = code
		?.takeIf { it.isNotBlank() }
		?.let { runCatching { Locale(it).isO3Language }.getOrNull() }
		?.takeIf { it.length == 3 }

	private fun search(activity: FragmentActivity, itemId: String, language: String) {
		Toast.makeText(activity, R.string.msg_searching_subtitles, Toast.LENGTH_SHORT).show()
		activity.lifecycleScope.launch {
			val results = try {
				withContext(Dispatchers.IO) {
					api.get<JsonArray>("/Items/{itemId}/RemoteSearch/Subtitles/{language}", mapOf("itemId" to itemId, "language" to language)).content
				}.mapNotNull { element -> toResult(element.jsonObject) }
			} catch (err: InvalidStatusException) {
				Timber.w(err, "Subtitle search failed")
				val message = if (err.status == 401 || err.status == 403) R.string.msg_subtitles_no_permission else R.string.msg_subtitle_search_failed
				if (!activity.isFinishing) Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
				return@launch
			} catch (err: Exception) {
				Timber.w(err, "Subtitle search failed")
				if (!activity.isFinishing) Toast.makeText(activity, R.string.msg_subtitle_search_failed, Toast.LENGTH_LONG).show()
				return@launch
			}

			if (activity.isFinishing || activity.isDestroyed) return@launch
			if (results.isEmpty()) {
				Toast.makeText(activity, R.string.msg_no_subtitles_found, Toast.LENGTH_LONG).show()
				return@launch
			}

			AlertDialog.Builder(activity)
				.setTitle(R.string.lbl_download_subtitles)
				.setItems(results.map { it.label }.toTypedArray()) { _, which -> download(activity, itemId, results[which]) }
				.show()
		}
	}

	private fun toResult(json: JsonObject): Result? {
		fun string(key: String) = json[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

		val id = string("Id") ?: return null
		val label = buildString {
			append(string("Name") ?: id)
			val details = listOfNotNull(
				string("ThreeLetterISOLanguageName")?.uppercase(Locale.ROOT),
				string("Format")?.uppercase(Locale.ROOT),
				string("ProviderName"),
				string("DownloadCount")?.let { "↓$it" },
				"forced".takeIf { string("IsForced") == "true" },
				"HI".takeIf { string("IsHearingImpaired") == "true" },
			)
			if (details.isNotEmpty()) append("\n").append(details.joinToString(" · "))
		}
		return Result(id, label)
	}

	private fun download(activity: FragmentActivity, itemId: String, result: Result) {
		activity.lifecycleScope.launch {
			val ok = try {
				withContext(Dispatchers.IO) {
					api.post<Unit>("/Items/{itemId}/RemoteSearch/Subtitles/{subtitleId}", mapOf("itemId" to itemId, "subtitleId" to result.id))
				}
				true
			} catch (err: Exception) {
				Timber.w(err, "Subtitle download failed")
				false
			}
			if (activity.isFinishing || activity.isDestroyed) return@launch
			Toast.makeText(activity, if (ok) R.string.msg_subtitle_downloaded else R.string.msg_subtitle_download_failed, Toast.LENGTH_LONG).show()
		}
	}
}
