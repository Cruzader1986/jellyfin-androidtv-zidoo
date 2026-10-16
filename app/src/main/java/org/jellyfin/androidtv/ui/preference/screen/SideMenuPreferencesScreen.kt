package org.jellyfin.androidtv.ui.preference.screen

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.home.HomeSideRail
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.checkbox
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.android.ext.android.inject

/** Which entries show in the side menu of the modern home screen. */
class SideMenuPreferencesScreen : OptionsFragment() {
	private val api by inject<ApiClient>()

	// Same list as the side menu itself (every library, nothing filtered out)
	private var libraries: List<BaseItemDto> = emptyList()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		lifecycleScope.launch {
			libraries = withContext(Dispatchers.IO) {
				runCatching { api.userViewsApi.getUserViews().content.items.orEmpty() }.getOrDefault(emptyList())
			}
			rebuild()
		}
	}

	override val screen by optionsScreen {
		setTitle(R.string.pref_side_menu)

		fun org.jellyfin.androidtv.ui.preference.dsl.OptionsCategory.entry(key: String, label: String) = checkbox {
			title = label
			bind {
				get { key !in HomeSideRail.hiddenEntries(requireContext()) }
				set { show -> HomeSideRail.setHidden(requireContext(), key, !show) }
				default { true }
			}
		}

		category {
			setTitle(R.string.pref_side_menu_description)

			entry(HomeSideRail.KEY_SEARCH, getString(R.string.lbl_search))
			checkbox {
				title = getString(R.string.lbl_rail_home)
				setContent(R.string.pref_side_menu_home_always)
				enabled = false
				bind {
					get { true }
					set { }
					default { true }
				}
			}
			libraries.forEach { view ->
				entry(HomeSideRail.KEY_LIBRARY_PREFIX + view.id, view.name.orEmpty())
			}
			entry(HomeSideRail.KEY_SETTINGS, getString(R.string.lbl_rail_settings))
			entry(HomeSideRail.KEY_SWITCH_USER, getString(R.string.lbl_switch_user))
		}
	}
}
