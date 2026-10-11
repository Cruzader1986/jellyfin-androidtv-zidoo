package org.jellyfin.androidtv.util.update

import android.app.AlertDialog
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

object UpdatePrompt : KoinComponent {
	private val preferences by inject<UserPreferences>()
	private var checkedThisSession = false

	/** Automatic check, at most once per app process and only when enabled in settings. */
	fun checkOnStartup(activity: FragmentActivity) {
		if (checkedThisSession) return
		checkedThisSession = true
		if (!preferences[UserPreferences.checkForUpdates]) return
		check(activity, manual = false)
	}

	fun check(activity: FragmentActivity, manual: Boolean) {
		if (manual) Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show()

		activity.lifecycleScope.launch {
			val release = AppUpdater.fetchLatestRelease()
			if (activity.isFinishing || activity.isDestroyed) return@launch

			if (release == null) {
				if (manual) Toast.makeText(activity, R.string.update_check_failed, Toast.LENGTH_LONG).show()
				return@launch
			}

				val newer = AppUpdater.isNewer(release.version, AppUpdater.currentVersion)
			val skipped = preferences[UserPreferences.skippedUpdateVersion] == release.version
			when {
				!newer -> if (manual) Toast.makeText(activity, activity.getString(R.string.update_up_to_date, AppUpdater.currentVersion), Toast.LENGTH_LONG).show()
				skipped && !manual -> Timber.i("Update %s skipped by user", release.version)
				else -> showUpdateDialog(activity, release)
			}
		}
	}

	private fun showUpdateDialog(activity: FragmentActivity, release: AppUpdater.Release) {
		val notes = release.notes.trim().let { if (it.length > 700) it.take(700) + "…" else it }
		val message = buildString {
			append(activity.getString(R.string.update_available_message, release.version, AppUpdater.currentVersion))
			if (notes.isNotEmpty()) append("\n\n").append(notes)
		}

		AlertDialog.Builder(activity)
			.setTitle(R.string.update_available_title)
			.setMessage(message)
			.setPositiveButton(R.string.update_install) { _, _ -> downloadAndInstall(activity, release) }
			.setNeutralButton(R.string.update_skip) { _, _ ->
				preferences[UserPreferences.skippedUpdateVersion] = release.version
			}
			.setNegativeButton(R.string.update_later, null)
			.show()
	}

	private fun downloadAndInstall(activity: FragmentActivity, release: AppUpdater.Release) {
		if (!AppUpdater.ensureInstallPermission(activity)) {
			Toast.makeText(activity, R.string.update_allow_install, Toast.LENGTH_LONG).show()
			return
		}

		val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
			max = 100
			isIndeterminate = true
			val padding = (24 * resources.displayMetrics.density).toInt()
			setPadding(padding, padding / 2, padding, padding / 2)
		}
		val dialog = AlertDialog.Builder(activity)
			.setTitle(activity.getString(R.string.update_downloading, release.version))
			.setView(progressBar)
			.setCancelable(false)
			.show()

		activity.lifecycleScope.launch {
			try {
				val apk = AppUpdater.download(activity, release) { progress ->
					if (progress >= 0) {
						progressBar.isIndeterminate = false
						progressBar.progress = progress
					}
				}
				dialog.dismiss()
				AppUpdater.install(activity, apk)
			} catch (err: Exception) {
				Timber.e(err, "Update download failed")
				dialog.dismiss()
				Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show()
			}
		}
	}
}
