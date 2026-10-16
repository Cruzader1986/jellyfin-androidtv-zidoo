package org.jellyfin.androidtv.ui.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.preference.PreferencesActivity
import org.jellyfin.androidtv.ui.search.SearchActivity
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Side menu of the modern home screen (Google TV / Apple TV style): a slim icon bar on the left
 * that opens with labels when it gets focus. Search, Home, every library, Settings, Switch user.
 *
 * Entries can be hidden: long-press OK (or press Menu) on any entry to edit the menu, then OK shows or
 * hides an entry and Back is done. Home always stays so the menu can be edited again.
 * The same choice can be made in Settings > Customization > Side menu items.
 */
class HomeSideRail(
	private val activity: Activity,
	private val onHome: () -> Unit,
	private val onLeave: () -> Unit,
	private val onExpandedChanged: (expanded: Boolean, extraWidthPx: Int) -> Unit,
) : FrameLayout(activity), KoinComponent {
	private val api by inject<ApiClient>()
	private val sessionRepository by inject<SessionRepository>()

	private val density = resources.displayMetrics.density
	private fun dp(value: Int) = (value * density).toInt()

	private val panel = LinearLayout(activity).apply {
		orientation = LinearLayout.VERTICAL
		setPadding(dp(12), dp(28), dp(12), dp(24))
	}
	private val topItems = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
	private val libraryItems = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
	private val bottomItems = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
	private val labels = mutableListOf<TextView>()
	private var expanded = false

	// Hiding entries
	private class Entry(val key: String, val view: View, val label: TextView, val baseLabel: String)
	private val entries = mutableListOf<Entry>()
	private val prefs = railPrefs(activity)
	private var hidden: Set<String> = hiddenEntries(activity)
	// Changes made in Settings while the home screen stays open
	private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
		if (key == PREF_HIDDEN) {
			hidden = hiddenEntries(activity)
			applyHidden()
		}
	}
	private var editing = false
	private val editHint = TextView(activity).apply {
		text = activity.getString(R.string.lbl_rail_edit_hint)
		textSize = 13f
		setTextColor(Color.rgb(224, 164, 88))
		setPadding(dp(14), 0, dp(14), dp(12))
		visibility = GONE
	}

	init {
		clipChildren = false
		panel.addView(editHint)
		panel.addView(topItems)
		panel.addView(libraryItems)
		panel.addView(View(activity), LinearLayout.LayoutParams(1, 0, 1f))
		panel.addView(bottomItems)
		addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

		addItem(topItems, KEY_SEARCH, R.drawable.ic_search, R.string.lbl_search) {
			activity.startActivity(Intent(activity, SearchActivity::class.java))
		}
		addItem(topItems, KEY_HOME, R.drawable.ic_house, R.string.lbl_rail_home) { onHome() }.isSelected = true
		addItem(bottomItems, KEY_SETTINGS, R.drawable.ic_settings, R.string.lbl_rail_settings) {
			activity.startActivity(Intent(activity, PreferencesActivity::class.java))
		}
		addItem(bottomItems, KEY_SWITCH_USER, R.drawable.ic_switch_users, R.string.lbl_switch_user) { switchUser() }

		applyHidden()
		setExpanded(false)
	}

	/** Load the libraries into the menu. */
	fun load(scope: LifecycleCoroutineScope) = scope.launch {
		val views = withContext(Dispatchers.IO) {
			runCatching { api.userViewsApi.getUserViews().content.items.orEmpty() }
				.onFailure { Timber.w(it, "Unable to load libraries for the side menu") }
				.getOrDefault(emptyList())
		}
		libraryItems.removeAllViews()
		entries.removeAll { it.key.startsWith(KEY_LIBRARY_PREFIX) }
		views.forEach { view ->
			addItem(libraryItems, KEY_LIBRARY_PREFIX + view.id, iconFor(view), view.name.orEmpty()) { openLibrary(view) }
		}
		applyHidden()
		setExpanded(expanded)
	}

	private fun iconFor(view: BaseItemDto): Int = when (view.collectionType?.lowercase()) {
		"movies" -> R.drawable.ic_movie
		"tvshows" -> R.drawable.ic_tv
		"music" -> R.drawable.ic_music_album
		"livetv" -> R.drawable.ic_tv_guide
		"homevideos", "photos" -> R.drawable.ic_photos
		"playlists" -> R.drawable.ic_video_queue
		"boxsets" -> R.drawable.ic_clapperboard
		else -> R.drawable.ic_folder
	}

	private fun openLibrary(view: BaseItemDto) {
		ItemLauncher.createUserViewIntent(view, activity) { intent -> activity.startActivity(intent) }
	}

	private fun switchUser() {
		sessionRepository.destroyCurrentSession()
		activity.startActivity(Intent(activity, StartupActivity::class.java).apply {
			putExtra(StartupActivity.EXTRA_HIDE_SPLASH, true)
			addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
		})
		activity.finishAfterTransition()
	}

	private fun addItem(parent: LinearLayout, key: String, @DrawableRes icon: Int, label: Int, onClick: () -> Unit): View =
		addItem(parent, key, icon, context.getString(label), onClick)

	private fun addItem(parent: LinearLayout, key: String, @DrawableRes icon: Int, label: String, onClick: () -> Unit): View {
		val colors = ColorStateList(
			arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf(android.R.attr.state_selected), intArrayOf()),
			intArrayOf(Color.rgb(11, 13, 18), Color.WHITE, Color.rgb(174, 182, 196)),
		)
		fun pill(color: Int) = GradientDrawable().apply {
			cornerRadius = dp(12).toFloat()
			setColor(color)
		}
		val item = LinearLayout(context).apply {
			id = View.generateViewId()
			orientation = LinearLayout.HORIZONTAL
			gravity = Gravity.CENTER_VERTICAL
			isFocusable = true
			// Mouse / touch: one click opens the entry instead of only focusing it
			isFocusableInTouchMode = false
			isClickable = true
			setPadding(dp(14), 0, dp(14), 0)
			contentDescription = label
			background = StateListDrawable().apply {
				addState(intArrayOf(android.R.attr.state_focused), pill(Color.rgb(242, 244, 248)))
				addState(intArrayOf(android.R.attr.state_pressed), pill(Color.argb(160, 242, 244, 248)))
				addState(intArrayOf(android.R.attr.state_selected), pill(Color.argb(46, 255, 255, 255)))
			}
			setOnClickListener { if (editing) toggleHidden(key) else onClick() }
			// Long-press OK: edit the menu (show / hide entries)
			setOnLongClickListener {
				setEditing(!editing)
				true
			}
			setOnKeyListener { _, keyCode, event ->
				if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
				when (keyCode) {
					android.view.KeyEvent.KEYCODE_MENU -> {
						setEditing(!editing)
						true
					}
					android.view.KeyEvent.KEYCODE_BACK -> {
						if (editing) setEditing(false) else onLeave()
						true
					}
					android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
						if (editing) setEditing(false)
						onLeave()
						true
					}
					else -> false
				}
			}
		}
		item.addView(ImageView(context).apply {
			setImageResource(icon)
			imageTintList = colors
			isDuplicateParentStateEnabled = true
		}, LinearLayout.LayoutParams(dp(24), dp(24)))
		val text = TextView(context).apply {
			text = label
			textSize = 17f
			setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
			setTextColor(colors)
			isDuplicateParentStateEnabled = true
			maxLines = 1
			setPadding(dp(16), 0, 0, 0)
		}
		labels += text
		item.addView(text)
		parent.addView(item, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)).apply { bottomMargin = dp(4) })
		entries += Entry(key, item, text, label)
		return item
	}

	private fun toggleHidden(key: String) {
		if (key == KEY_HOME) return // Home stays, it is the way back into editing
		setHidden(context, key, key !in hidden)
	}

	private fun setEditing(value: Boolean) {
		if (value == editing) return
		editing = value
		editHint.visibility = if (value && expanded) VISIBLE else GONE
		val focused = entries.firstOrNull { it.view.hasFocus() }
		applyHidden()
		// The focused entry may just have been hidden: go to Home instead
		if (!value && focused != null && focused.view.visibility != VISIBLE) focusMenu()
	}

	/** Hidden entries are gone, except while editing: then they show dimmed and marked as hidden. */
	private fun applyHidden() {
		val hiddenLabel = context.getString(R.string.lbl_rail_hidden)
		entries.forEach { entry ->
			val isHidden = entry.key != KEY_HOME && entry.key in hidden
			entry.view.visibility = if (isHidden && !editing) GONE else VISIBLE
			entry.view.alpha = if (isHidden && editing) 0.4f else 1f
			entry.label.text = if (isHidden && editing) "${entry.baseLabel}  ·  $hiddenLabel" else entry.baseLabel
		}
		linkFocus()
	}

	/**
	 * Up/down go from entry to entry in menu order. Without this, Down from the last library jumped
	 * to the home rows on the right (the Settings entries sit far below, past the empty space).
	 * The ends stay put instead of leaving the menu.
	 */
	private fun linkFocus() {
		val visible = listOf(topItems, libraryItems, bottomItems).flatMap { group ->
			(0 until group.childCount).map { group.getChildAt(it) }.filter { it.visibility == VISIBLE }
		}
		visible.forEachIndexed { index, view ->
			view.nextFocusUpId = (visible.getOrNull(index - 1) ?: view).id
			view.nextFocusDownId = (visible.getOrNull(index + 1) ?: view).id
			view.nextFocusLeftId = view.id
		}
	}

	private val focusListener = android.view.ViewTreeObserver.OnGlobalFocusChangeListener { _, _ ->
		val focused = hasFocus()
		if (!focused && editing) setEditing(false)
		if (focused != expanded) setExpanded(focused)
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)
		prefs.registerOnSharedPreferenceChangeListener(prefsListener)
		hidden = hiddenEntries(context)
		applyHidden()
		setExpanded(hasFocus())
	}

	override fun onDetachedFromWindow() {
		viewTreeObserver.removeOnGlobalFocusChangeListener(focusListener)
		prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
		super.onDetachedFromWindow()
	}

	/** Move the focus into the menu, on its Home entry. */
	fun focusMenu() {
		(topItems.getChildAt(1) ?: topItems.getChildAt(0))?.requestFocus()
	}

	private fun setExpanded(value: Boolean) {
		val changed = value != expanded
		expanded = value
		if (changed) onExpandedChanged(value, if (value) (EXPANDED_WIDTH_DP - COLLAPSED_WIDTH_DP).toPx() else 0)
		labels.forEach { it.visibility = if (value) VISIBLE else GONE }
		editHint.visibility = if (value && editing) VISIBLE else GONE
		layoutParams?.let {
			it.width = if (value) EXPANDED_WIDTH_DP.toPx() else COLLAPSED_WIDTH_DP.toPx()
			layoutParams = it
		}
		setBackgroundColor(if (value) Color.argb(245, 16, 20, 28) else Color.argb(140, 7, 9, 13))
		elevation = if (value) dp(16).toFloat() else 0f
	}

	private fun Int.toPx() = dp(this)

	companion object {
		const val COLLAPSED_WIDTH_DP = 76
		const val EXPANDED_WIDTH_DP = 280

		private const val PREF_HIDDEN = "hidden_entries"
		const val KEY_SEARCH = "search"
		const val KEY_HOME = "home"
		const val KEY_SETTINGS = "settings"
		const val KEY_SWITCH_USER = "switch_user"
		const val KEY_LIBRARY_PREFIX = "library:"

		private fun railPrefs(context: Context) = context.getSharedPreferences("home_side_rail", Context.MODE_PRIVATE)

		/** Keys of the entries hidden from the side menu (shared by the menu and its Settings screen). */
		fun hiddenEntries(context: Context): Set<String> = railPrefs(context).getStringSet(PREF_HIDDEN, emptySet()).orEmpty().toSet()

		fun setHidden(context: Context, key: String, hide: Boolean) {
			if (key == KEY_HOME) return
			val current = hiddenEntries(context)
			railPrefs(context).edit().putStringSet(PREF_HIDDEN, if (hide) current + key else current - key).apply()
		}

		@JvmStatic
		fun collapsedWidth(activity: Activity) = (COLLAPSED_WIDTH_DP * activity.resources.displayMetrics.density).toInt()

		fun layoutParams(activity: Activity) = FrameLayout.LayoutParams(collapsedWidth(activity), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START)
	}
}
