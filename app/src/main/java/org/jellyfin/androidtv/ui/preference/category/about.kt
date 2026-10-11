package org.jellyfin.androidtv.ui.preference.category

import android.os.Build
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.preference.dsl.OptionsScreen
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.preference.dsl.action
import org.jellyfin.androidtv.ui.preference.dsl.checkbox
import org.jellyfin.androidtv.ui.preference.dsl.link
import org.jellyfin.androidtv.util.update.UpdatePrompt
import org.koin.java.KoinJavaComponent
import org.jellyfin.androidtv.ui.preference.screen.LicensesScreen
import org.jellyfin.androidtv.util.DeviceUtils

fun OptionsScreen.aboutCategory() = category {
	val categoryContext = context

	setTitle(R.string.pref_about_title)

	link {
		// Hardcoded strings for troubleshooting purposes
		title = "Jellyfin app version"
		content = "jellyfin-androidtv ${BuildConfig.VERSION_NAME} ${BuildConfig.BUILD_TYPE}"
		icon = R.drawable.ic_jellyfin
	}

	action {
		setTitle(R.string.update_check_now)
		content = categoryContext.getString(R.string.update_check_now_description, org.jellyfin.androidtv.util.update.AppUpdater.REPOSITORY)
		icon = R.drawable.ic_jellyfin
		onActivate = {
			(categoryContext as? androidx.fragment.app.FragmentActivity)?.let { UpdatePrompt.check(it, manual = true) }
		}
	}

	checkbox {
		setTitle(R.string.update_check_on_startup)
		setContent(R.string.update_check_on_startup_enabled, R.string.update_check_on_startup_disabled)
		bind(KoinJavaComponent.get<UserPreferences>(UserPreferences::class.java), UserPreferences.checkForUpdates)
	}

	link {
		setTitle(R.string.pref_device_model)
		content = "${Build.BRAND} ${Build.MODEL}/${Build.MANUFACTURER} , Android: ${Build.VERSION.RELEASE}, API: ${Build.VERSION.SDK_INT}"
		icon = R.drawable.ic_tv
	}

	link {
		setTitle(R.string.pref_device_fw)
		content = "${DeviceUtils.getSystemPropertyCached("ro.product.version")}\n" +
				"New Zidoo API: ${DeviceUtils.hasNewZidooApi()}"
		icon = R.drawable.ic_tv
	}

	link {
		setTitle(R.string.licenses_link)
		setContent(R.string.licenses_link_description)
		icon = R.drawable.ic_guide
		withFragment<LicensesScreen>()
	}
}
