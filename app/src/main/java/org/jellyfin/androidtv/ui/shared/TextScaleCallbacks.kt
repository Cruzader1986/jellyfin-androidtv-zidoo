package org.jellyfin.androidtv.ui.shared

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import org.jellyfin.androidtv.preference.UserPreferences
import timber.log.Timber

/**
 * Applies the "Text size" setting to every activity before its views are inflated, by scaling the
 * font scale of the activity resources. All text sized in sp follows automatically.
 */
class TextScaleCallbacks(
	private val userPreferences: UserPreferences
) : AbstractActivityLifecycleCallbacks() {
	private val appliedScale = mutableMapOf<Int, Float>()

	override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) activity.applyTextScale()
	}

	override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) activity.applyTextScale()
	}

	override fun onActivityResumed(activity: Activity) {
		val applied = appliedScale[System.identityHashCode(activity)] ?: return
		if (applied != currentFactor()) {
			Timber.i("Recreating activity to apply new text size")
			activity.recreate()
		}
	}

	override fun onActivityDestroyed(activity: Activity) {
		appliedScale.remove(System.identityHashCode(activity))
	}

	private fun currentFactor() = userPreferences[UserPreferences.textScale].factor

	@Suppress("DEPRECATION")
	private fun Activity.applyTextScale() {
		val factor = currentFactor()
		appliedScale[System.identityHashCode(this)] = factor

		val configuration = Configuration(resources.configuration)
		configuration.fontScale = Resources.getSystem().configuration.fontScale * factor
		resources.updateConfiguration(configuration, resources.displayMetrics)
	}
}
