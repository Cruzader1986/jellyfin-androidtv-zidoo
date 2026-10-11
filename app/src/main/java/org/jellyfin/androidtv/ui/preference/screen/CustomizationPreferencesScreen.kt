package org.jellyfin.androidtv.ui.preference.screen

import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.*
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.AppTheme
import org.jellyfin.androidtv.preference.constant.ClockBehavior
import org.jellyfin.androidtv.preference.constant.RatingType
import org.jellyfin.androidtv.preference.constant.TextScale
import org.jellyfin.androidtv.preference.constant.WatchedIndicatorBehavior
import org.jellyfin.androidtv.ui.preference.dsl.*
import org.koin.android.ext.android.inject

class CustomizationPreferencesScreen : OptionsFragment() {
	private val userPreferences: UserPreferences by inject()

	override val screen by optionsScreen {
		setTitle(R.string.pref_customization)

		category {
			setTitle(R.string.pref_theme)

			enum<AppTheme> {
				setTitle(R.string.pref_app_theme)
				bind(userPreferences, UserPreferences.appTheme)
			}

			enum<TextScale> {
				setTitle(R.string.pref_text_scale)
				bind(userPreferences, UserPreferences.textScale)
			}

			enum<ClockBehavior> {
				setTitle(R.string.pref_clock_display)
				bind(userPreferences, UserPreferences.clockBehavior)
			}

			enum<BackgroundType> {
				setTitle(R.string.lbl_show_backdrop)
				bind(userPreferences, UserPreferences.backdropType)
			}

			checkbox {
				setTitle(R.string.pref_backdrop_blur)
				setContent(R.string.pref_backdrop_blur_description)
				bind(userPreferences, UserPreferences.backdropBlur)
				depends { userPreferences[UserPreferences.backdropType] != BackgroundType.NONE }
			}

			checkbox {
				setTitle(R.string.pref_theme_music)
				setContent(R.string.pref_theme_music_description)
				bind(userPreferences, UserPreferences.themeMusicEnabled)
			}

			checkbox {
				setTitle(R.string.lbl_show_premieres)
				setContent(R.string.desc_premieres)
				bind(userPreferences, UserPreferences.premieresEnabled)
			}

			checkbox {
				setTitle(R.string.pref_hide_parental_ratings)
				bind(userPreferences, UserPreferences.hideParentalRatings)
			}
		}

		category {
			setTitle(R.string.pref_browsing)

			link {
				setTitle(R.string.home_prefs)
				setContent(R.string.pref_home_description)
				icon = R.drawable.ic_house
				withFragment<HomePreferencesScreen>()
			}

			link {
				setTitle(R.string.pref_libraries)
				setContent(R.string.pref_libraries_description)
				icon = R.drawable.ic_grid
				withFragment<LibrariesPreferencesScreen>()
			}
		}

//		category {
//			setTitle(R.string.pref_behavior)
//
//			checkbox {
//				setTitle(R.string.pref_clear_audio_queue_on_exit)
//				bind(userPreferences, UserPreferences.clearAudioQueueOnExit)
//			}
//
////			shortcut {
////				setTitle(R.string.pref_audio_track_button)
////				bind(userPreferences, UserPreferences.shortcutAudioTrack)
////			}
////
////			shortcut {
////				setTitle(R.string.pref_subtitle_track_button)
////				bind(userPreferences, UserPreferences.shortcutSubtitleTrack)
////			}
//		}

		category {
			setTitle(R.string.lbl_card_styles)

			enum<WatchedIndicatorBehavior> {
				setTitle(R.string.pref_watched_indicator)
				bind(userPreferences, UserPreferences.watchedIndicatorBehavior)
			}

			enum<RatingType> {
				setTitle(R.string.pref_default_rating)
				bind(userPreferences, UserPreferences.defaultRatingType)
			}

			seekbar {
				setTitle(R.string.pref_card_corners)
				min = 0
				max = 12
				increment = 2
				bind(userPreferences, UserPreferences.cardCornerRounding)
			}

			enum<CardSpacing> {
				setTitle(R.string.pref_card_spacing)
				bind(userPreferences, UserPreferences.cardSpacing)
			}

			enum<ColorSelectionBG> {
				setTitle(R.string.pref_card_color_bg)
				bind(userPreferences, UserPreferences.cardColorBG)
			}
		}

		category {
			setTitle(R.string.lbl_card_focus_styles)

			checkbox {
				setTitle(R.string.pref_enable_focus_dimming)
				bind(userPreferences, UserPreferences.enableFocusDimming)
			}

			enum<FocusZoomSize> {
				setTitle(R.string.pref_card_focus_zoom_size)
				bind(userPreferences, UserPreferences.focusZoomSize)
			}

			enum<FocusBorderSize> {
				setTitle(R.string.pref_card_focus_border_size)
				bind(userPreferences, UserPreferences.focusBorderSize)
			}

			enum<ColorSelection> {
				setTitle(R.string.pref_card_focus_border_color)
				bind(userPreferences, UserPreferences.focusBorderColor)
				depends { userPreferences[UserPreferences.focusBorderSize] != FocusBorderSize.NONE }
			}

			enum<FocusIconSize> {
				setTitle(R.string.pref_card_focus_icon_size)
				bind(userPreferences, UserPreferences.focusIconSize)
			}
		}
	}
}
