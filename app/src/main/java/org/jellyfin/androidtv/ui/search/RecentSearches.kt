package org.jellyfin.androidtv.ui.search

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import org.json.JSONArray

/** A previous search shown on the empty search screen. */
data class RecentSearchItem(val query: String)

/**
 * Remembers the last searches (the ones you opened a result from, or submitted) on this device.
 */
object RecentSearches {
	private const val PREFS = "recent_searches"
	private const val KEY = "queries"
	private const val MAX = 10

	private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

	@JvmStatic
	fun get(context: Context): List<String> = runCatching {
		val array = JSONArray(prefs(context).getString(KEY, "[]"))
		(0 until array.length()).map { array.getString(it) }
	}.getOrDefault(emptyList())

	@JvmStatic
	fun add(context: Context, query: String?) {
		val trimmed = query?.trim().orEmpty()
		if (trimmed.length < 2) return
		val updated = listOf(trimmed) + get(context).filterNot { it.equals(trimmed, ignoreCase = true) }
		prefs(context).edit().putString(KEY, JSONArray(updated.take(MAX)).toString()).apply()
	}

	@JvmStatic
	fun clear(context: Context) = prefs(context).edit().remove(KEY).apply()
}

/** Simple focusable text chip for [RecentSearchItem]. */
class RecentSearchPresenter : Presenter() {
	override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
		val density = parent.resources.displayMetrics.density
		fun pill(color: Int) = GradientDrawable().apply {
			cornerRadius = 24 * density
			setColor(color)
		}

		val view = TextView(parent.context).apply {
			isFocusable = true
			isFocusableInTouchMode = true
			gravity = Gravity.CENTER
			textSize = 18f
			setTextColor(Color.WHITE)
			minWidth = (120 * density).toInt()
			setPadding((24 * density).toInt(), (12 * density).toInt(), (24 * density).toInt(), (12 * density).toInt())
			background = StateListDrawable().apply {
				addState(intArrayOf(android.R.attr.state_focused), pill(Color.argb(200, 255, 255, 255)))
				addState(intArrayOf(), pill(Color.argb(50, 255, 255, 255)))
			}
			setOnFocusChangeListener { v, hasFocus -> (v as TextView).setTextColor(if (hasFocus) Color.BLACK else Color.WHITE) }
		}
		return ViewHolder(view)
	}

	override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
		(viewHolder.view as TextView).text = (item as? RecentSearchItem)?.query.orEmpty()
	}

	override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
