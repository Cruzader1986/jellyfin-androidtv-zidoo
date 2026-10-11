package org.jellyfin.androidtv.ui.preference.screen

import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.TelemetryPreferences
import android.app.AlertDialog
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.jellyfin.androidtv.telemetry.CrashLog
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.action
import org.jellyfin.androidtv.ui.preference.dsl.checkbox
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.koin.android.ext.android.inject

class CrashReportingPreferencesScreen : OptionsFragment() {
	private val telemetryPreferences: TelemetryPreferences by inject()

	override val screen by optionsScreen {
		setTitle(R.string.pref_telemetry_category)

		category {
			checkbox {
				setTitle(R.string.pref_crash_reports)
				setContent(R.string.pref_crash_reports_enabled, R.string.pref_crash_reports_disabled)
				bind(telemetryPreferences, TelemetryPreferences.crashReportEnabled)
			}

			checkbox {
				setTitle(R.string.pref_crash_report_logs)
				setContent(R.string.pref_crash_report_logs_enabled, R.string.pref_crash_report_logs_disabled)
				bind(telemetryPreferences, TelemetryPreferences.crashReportIncludeLogs)
				depends { telemetryPreferences[TelemetryPreferences.crashReportEnabled] }
			}
		}

		category {
			setTitle(R.string.pref_last_crash)

			action {
				setTitle(R.string.pref_view_last_crash)
				content = CrashLog.read(requireContext())?.lineSequence()?.firstOrNull() ?: getString(R.string.pref_no_crash_logged)
				onActivate = { showLastCrash() }
			}
		}
	}

	private fun showLastCrash() {
		val context = requireContext()
		val log = CrashLog.read(context)
		if (log == null) {
			Toast.makeText(context, R.string.pref_no_crash_logged, Toast.LENGTH_SHORT).show()
			return
		}

		val padding = (16 * resources.displayMetrics.density).toInt()
		val text = TextView(context).apply {
			setPadding(padding, padding, padding, padding)
			textSize = 12f
			setText(log)
		}
		AlertDialog.Builder(context)
			.setTitle(R.string.pref_last_crash)
			.setView(ScrollView(context).apply { addView(text); isFocusable = true })
			.setPositiveButton(android.R.string.ok, null)
			.setNegativeButton(R.string.pref_clear_crash) { _, _ -> CrashLog.clear(context) }
			.show()
	}
}
