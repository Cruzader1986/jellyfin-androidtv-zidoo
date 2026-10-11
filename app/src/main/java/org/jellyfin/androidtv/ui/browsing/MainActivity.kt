package org.jellyfin.androidtv.ui.browsing

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
	private val backgroundService: BackgroundService by inject<BackgroundService>()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

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
