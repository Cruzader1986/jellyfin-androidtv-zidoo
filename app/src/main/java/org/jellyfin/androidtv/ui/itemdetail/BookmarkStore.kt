package org.jellyfin.androidtv.ui.itemdetail

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bookmarks (named positions) per movie/episode, stored on this device.
 */
object BookmarkStore {
	private const val PREFS = "bookmarks"

	data class Bookmark(val name: String, val positionTicks: Long)

	private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

	@JvmStatic
	fun get(context: Context, itemId: String?): List<Bookmark> {
		if (itemId.isNullOrBlank()) return emptyList()
		val raw = prefs(context).getString(itemId, null) ?: return emptyList()
		return runCatching {
			val array = JSONArray(raw)
			(0 until array.length()).map { i ->
				val json = array.getJSONObject(i)
				Bookmark(json.optString("name"), json.optLong("ticks"))
			}.sortedBy { it.positionTicks }
		}.getOrDefault(emptyList())
	}

	@JvmStatic
	fun add(context: Context, itemId: String, name: String, positionTicks: Long) =
		save(context, itemId, get(context, itemId).filterNot { it.positionTicks == positionTicks } + Bookmark(name, positionTicks))

	@JvmStatic
	fun remove(context: Context, itemId: String, bookmark: Bookmark) =
		save(context, itemId, get(context, itemId).filterNot { it == bookmark })

	private fun save(context: Context, itemId: String, bookmarks: List<Bookmark>) {
		val editor = prefs(context).edit()
		if (bookmarks.isEmpty()) editor.remove(itemId)
		else editor.putString(itemId, JSONArray().apply {
			bookmarks.sortedBy { it.positionTicks }.forEach { put(JSONObject().put("name", it.name).put("ticks", it.positionTicks)) }
		}.toString())
		editor.apply()
	}
}
