package org.jellyfin.androidtv.util.update

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
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

	/** Release notes without markdown noise and raw links (those are replaced by the "View here" link). */
	private fun cleanNotes(notes: String): String = notes
		.lineSequence()
		.filterNot { it.contains("Full Changelog", ignoreCase = true) }
		.map { line ->
			line.replace(Regex("""\[([^\]]+)]\(https?://[^)]+\)"""), "$1")
				.replace(Regex("""https?://\S+"""), "")
				.replace("**", "")
				.replace(Regex("""^#+\s*"""), "")
				.replace(Regex("""^\s*[-*]\s+"""), "• ")
				.trimEnd()
		}
		.joinToString("\n")
		.replace(Regex("""\n{3,}"""), "\n\n")
		.trim()

	private fun showUpdateDialog(activity: FragmentActivity, release: AppUpdater.Release) {
		val notes = cleanNotes(release.notes).let { if (it.length > 700) it.take(700) + "…" else it }
		val density = activity.resources.displayMetrics.density
		val padding = (24 * density).toInt()

		val message = TextView(activity).apply {
			text = buildString {
				append(activity.getString(R.string.update_available_message, release.version, AppUpdater.currentVersion))
				if (notes.isNotEmpty()) append("\n\n").append(notes)
			}
			textSize = 16f
		}
		val link = TextView(activity).apply {
			val label = activity.getString(R.string.update_view_changes) + " "
			val linkText = activity.getString(R.string.update_view_here)
			text = SpannableString(label + linkText).apply {
				setSpan(UnderlineSpan(), label.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), label.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}
			textSize = 16f
			setTextColor(ContextCompat.getColor(activity, R.color.white))
			setPadding(0, (12 * density).toInt(), 0, (4 * density).toInt())
			isFocusable = true
			isClickable = true
			background = with(TypedValue()) {
				activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, this, true)
				ContextCompat.getDrawable(activity, resourceId)
			}
			setOnClickListener { openReleasePage(activity, release) }
		}
		val content = LinearLayout(activity).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(padding, padding / 2, padding, 0)
			addView(message)
			addView(link, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
		}

		AlertDialog.Builder(activity)
			.setTitle(R.string.update_available_title)
			.setView(ScrollView(activity).apply { addView(content) })
			.setPositiveButton(R.string.update_install) { _, _ -> downloadAndInstall(activity, release) }
			.setNeutralButton(R.string.update_skip) { _, _ ->
				preferences[UserPreferences.skippedUpdateVersion] = release.version
			}
			.setNegativeButton(R.string.update_later, null)
			.show()
	}

	private fun openReleasePage(activity: FragmentActivity, release: AppUpdater.Release) {
		val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl))
		try {
			// Android TV ships a stub that only says "You don't have an app that can do this", ignore it
			@Suppress("DEPRECATION")
			val browsers = activity.packageManager.queryIntentActivities(intent, 0)
				.filterNot { it.activityInfo?.packageName?.contains("frameworkpackagestubs") == true }
			if (browsers.isEmpty()) throw ActivityNotFoundException()
			activity.startActivity(intent)
		} catch (err: ActivityNotFoundException) {
			// Most TV boxes have no browser, show where to look instead
			Toast.makeText(activity, activity.getString(R.string.update_no_browser, release.pageUrl), Toast.LENGTH_LONG).show()
		}
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
