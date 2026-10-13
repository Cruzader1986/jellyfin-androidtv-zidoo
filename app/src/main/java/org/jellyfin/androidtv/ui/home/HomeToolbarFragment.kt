package org.jellyfin.androidtv.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.ui.ToolbarView
import org.jellyfin.androidtv.ui.preference.PreferencesActivity
import org.jellyfin.androidtv.ui.search.SearchActivity
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.jellyfin.androidtv.preference.UserPreferences
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject

class HomeToolbarFragment : Fragment() {
	private val sessionRepository by inject<SessionRepository>()

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		return ToolbarView(requireContext()).apply {
			// The modern layout has these in the side menu, keep only the clock
			if (get<UserPreferences>()[UserPreferences.modernHomeLayout]) return@apply

			addButton(R.drawable.ic_search, R.string.lbl_search) {
				val intent = Intent(activity, SearchActivity::class.java)
				activity?.startActivity(intent)
			}

			addButton(R.drawable.ic_settings, R.string.lbl_settings) {
				val intent = Intent(activity, PreferencesActivity::class.java)
				activity?.startActivity(intent)
			}

			addButton(R.drawable.ic_switch_users, R.string.lbl_switch_user) { switchUser() }
		}.rootView
	}

	private fun switchUser() {
		sessionRepository.destroyCurrentSession()

		// Open login activity
		val selectUserIntent = Intent(activity, StartupActivity::class.java)
		selectUserIntent.putExtra(StartupActivity.EXTRA_HIDE_SPLASH, true)
		// Remove history to prevent user going back to current activity
		selectUserIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)

		activity?.startActivity(selectUserIntent)
		activity?.finishAfterTransition()
	}
}
