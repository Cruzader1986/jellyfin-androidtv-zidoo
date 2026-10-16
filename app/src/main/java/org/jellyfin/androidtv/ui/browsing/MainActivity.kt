package org.jellyfin.androidtv.ui.browsing

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.leanback.widget.HorizontalGridView
import androidx.lifecycle.lifecycleScope
import org.jellyfin.androidtv.ui.home.HomePreviewView
import org.jellyfin.androidtv.ui.home.HomeSideRail
import android.widget.Toast
import androidx.fragment.app.add
import androidx.fragment.app.replace
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.home.HomeFragment
import org.jellyfin.androidtv.ui.home.HomeToolbarFragment
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.shared.BaseActivity
import org.jellyfin.androidtv.util.update.UpdatePrompt
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject

class MainActivity : BaseActivity(R.layout.fragment_content_view) {
	/** Preview of the focused card on the modern home screen (null with the classic layout). */
	var homePreview: HomePreviewView? = null
		private set
	private var sideRail: HomeSideRail? = null

	private fun homeFragment() = supportFragmentManager.fragments.firstOrNull { it is HomeFragment } as? HomeFragment

	override fun dispatchKeyEvent(event: KeyEvent): Boolean {
		// Leanback rows keep the focus at their first card; pressing left there opens the side menu
		val rail = sideRail
		if (rail != null && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && !rail.hasFocus()) {
			val focused = currentFocus
			val row = focused?.parent as? HorizontalGridView
			if (row != null && row.getChildAdapterPosition(focused) <= 0) {
				rail.focusMenu()
				return true
			}
		}
		return super.dispatchKeyEvent(event)
	}

	private val backgroundService: BackgroundService by inject<BackgroundService>()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		if (get<UserPreferences>()[UserPreferences.modernHomeLayout]) {
			val content = findViewById<ViewGroup>(R.id.content_view)
			homePreview = HomePreviewView(this).also {
				it.attach(lifecycleScope)
				// Full screen behind the rows: the backdrop fills it uncropped, the details use the top part
				content.addView(it, 0, ViewGroup.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.MATCH_PARENT,
				))
			}
			// Side menu goes on top of everything once the fragments are in
			content.post {
				val rail = HomeSideRail(
					this,
					onHome = { homeFragment()?.scrollToTop() },
					onLeave = {
						val grid = homeFragment()?.view
						if (grid?.requestFocus() != true) (grid as? ViewGroup)?.getChildAt(0)?.requestFocus()
					},
					onExpandedChanged = { _, extraWidth ->
						// Push the home content aside instead of covering it
						listOfNotNull(homePreview, homeFragment()?.view).forEach {
							it.animate().translationX(extraWidth.toFloat()).setDuration(180).start()
						}
					},
				)
				content.addView(rail, HomeSideRail.layoutParams(this))
				rail.load(lifecycleScope)
				sideRail = rail
			}
		}

		supportFragmentManager
			.beginTransaction()
			.replace<HomeToolbarFragment>(R.id.content_view)
			.add<HomeFragment>(R.id.content_view)
			.commit()

		backgroundService.attach(this)

		UpdatePrompt.checkOnStartup(this)
	}

	private var doubleBackToExitPressedOnce = false

	@Deprecated("Deprecated in Java")
	override fun onBackPressed() {
		if (doubleBackToExitPressedOnce) {
			if (get<UserPreferences>()[UserPreferences.clearAudioQueueOnExit])
				get<MediaManager>().clearAudioQueue(true)
			super.onBackPressed()
			finishAffinity()
			return
		}

		this.doubleBackToExitPressedOnce = true
		Toast.makeText(this, getString(R.string.exit_app_back_info), Toast.LENGTH_SHORT).show()

		Handler(Looper.getMainLooper()).postDelayed(Runnable { doubleBackToExitPressedOnce = false }, 2000)
	}
}
