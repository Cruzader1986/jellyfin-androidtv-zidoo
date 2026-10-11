package org.jellyfin.androidtv.preference

import android.content.Context
import android.view.KeyEvent
import androidx.preference.PreferenceManager
import org.jellyfin.androidtv.constant.*
import org.jellyfin.androidtv.preference.constant.*
import org.jellyfin.androidtv.util.DeviceUtils
import org.jellyfin.preference.booleanPreference
import org.jellyfin.preference.enumPreference
import org.jellyfin.preference.intPreference
import org.jellyfin.preference.store.SharedPreferenceStore
import org.jellyfin.preference.stringPreference

/**
 * User preferences are configurable by the user and change behavior of the application.
 * When changing preferences migration should be added to the init function.
 *
 * @param context Context to get the SharedPreferences from
 */
class UserPreferences(context: Context) : SharedPreferenceStore(
	sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
) {
	companion object {
		/**
		 * The value used for automatic detection in [maxBitrate].
		 */
		const val MAX_BITRATE_AUTO = "0"

/* Display */
		/**
		 * Select the app theme
		 */
		var appTheme = enumPreference("app_theme", AppTheme.DARK)

		/**
		 * App wide text size
		 */
		var textScale = enumPreference("pref_text_scale", TextScale.NORMAL)

		/**
		 * Enable background images while browsing
		 */
		var backdropType = enumPreference("pref_backdrop_type", BackgroundType.NORMAL)

		/**
		 * Blur backdrop images (rendered server side, so it also works on Android 9)
		 */
		var backdropBlur = booleanPreference("pref_backdrop_blur", true)

		/**
		 * Show premieres on home screen
		 */
		var premieresEnabled = booleanPreference("pref_enable_premieres", false)

/* Updates */
		/**
		 * Check GitHub releases for a newer version on startup
		 */
		var checkForUpdates = booleanPreference("pref_check_updates", true)

		/**
		 * Release version the user chose to skip
		 */
		var skippedUpdateVersion = stringPreference("pref_skipped_update_version", "")

/* Playback - General*/
		/**
		 * Maximum bitrate in megabit for playback. A value of [MAX_BITRATE_AUTO] is used when
		 * the bitrate should be automatically detected.
		 */
		var maxBitrate = stringPreference("pref_max_bitrate", "100")

		/**
		 * Auto-play next item
		 */
		var mediaQueuingEnabled = booleanPreference("pref_enable_tv_queuing", true)

		/**
		 * Enable the next up screen or not
		 */
		var nextUpBehavior = enumPreference("next_up_behavior", NextUpBehavior.MINIMAL)

		/**
		 * Next up timeout before playing next item
		 * Stored in milliseconds
		 */
		var nextUpTimeout = intPreference("next_up_timeout", 1000 * 7)

		/**
		 * Duration in seconds to subtract from resume time
		 */
		var resumeSubtractDuration = stringPreference("pref_resume_preroll", "0")

		/**
		 * Enable cinema mode
		 */
		var cinemaModeEnabled = booleanPreference("pref_enable_cinema_mode", false)

/* Playback - Video */
		/**
		 * Preferred video player.
		 */
		var videoPlayer = enumPreference("video_player", PreferredVideoPlayer.EXTERNAL)

		/**
		 * Change refresh rate to match media when device supports it
		 */
		var refreshRateSwitchingBehavior = enumPreference("refresh_rate_switching_behavior", RefreshRateSwitchingBehavior.DISABLED)

		/**
		 * Send a path instead to the external player
		 */
		var externalVideoPlayerSendPath = booleanPreference("pref_send_path_external", false)

		/**
		 * Automatically skip Jellyfin media segments (intro/credits) in the Zidoo player
		 */
		var segmentSkipBehavior = enumPreference("pref_segment_skip", SegmentSkipBehavior.OFF)

		/**
		 * Allow transcoding fallback in DirectPath Mode
		 */
		var enableTranscodingFallback = booleanPreference("pref_enable_transcoding_fallback", true)

		/* Playback - Audio related */
		/**
		 * Preferred behavior for audio streaming.
		 */
		var audioBehaviour = enumPreference("audio_behavior", defaultAudioBehavior)

		/**
		 * Enable DTS
		 */
		var dtsEnabled = booleanPreference("pref_bitstream_dts", false)

		/**
		 * Enable AC3
		 */
		var ac3Enabled = booleanPreference("pref_bitstream_ac3", !DeviceUtils.isFireTvStickGen1())

		/**
		 * Default audio delay in milliseconds for libVLC
		 */
		var libVLCAudioDelay = intPreference("libvlc_audio_delay", 0)

/* Live TV */
		/**
		 * Use direct play
		 */
		var liveTvDirectPlayEnabled = booleanPreference("pref_live_direct", true)

		/**
		 * Preferred video player for live TV
		 */
		var liveTvVideoPlayer = enumPreference("live_tv_video_player", PreferredVideoPlayer.AUTO)

		/**
		 * Shortcut used for changing the audio track
		 */
		var shortcutAudioTrack = intPreference("shortcut_audio_track", KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK)

		/**
		 * Shortcut used for changing the subtitle track
		 */
		var shortcutSubtitleTrack = intPreference("shortcut_subtitle_track", KeyEvent.KEYCODE_CAPTIONS)

/* Developer options */
		/**
		 * Show transcode toast/popup
		 */
		var showTranscodePlaybackPopup = booleanPreference("pref_show_transcode_playback_popup", false)

		/**
		 * Show additional debug information
		 */
		var debuggingEnabled = booleanPreference("pref_enable_debug", false)

		/**
		 * Use playback rewrite module
		 */
		var playbackRewriteEnabled = booleanPreference("playback_new", false)

		/**
		 * Use PictureViewer rewrite
		 */
		var pictureViewerRewriteEnabled = booleanPreference("picture_viewer_new", false)

		/**
		 * When to show the clock.
		 */
		var clockBehavior = enumPreference("pref_clock_behavior", ClockBehavior.ALWAYS)

		/**
		 * Disable display of Age-Restriction information (Text, Tags)
		 */
		var hideParentalRatings = booleanPreference("pref_hide_parental_ratings", false)

/* Card Styles */
		/**
		 * Set when watched indicators should show on MyImageCardViews
		 */
		var watchedIndicatorBehavior = enumPreference("pref_watched_indicator_behavior", WatchedIndicatorBehavior.ALWAYS)

		/**
		 * Set which ratings provider should show on MyImageCardViews
		 */
		var defaultRatingType = enumPreference("pref_rating_type", RatingType.RATING_TOMATOES)

		var enableFocusDimming = booleanPreference("pref_enable_focus_dimming", true)

		var focusZoomSize = enumPreference("pref_focus_zoom_size", FocusZoomSize.SMALL)

		var cardSpacing = enumPreference("pref_card_spacing", CardSpacing.NORMAL)

		var cardColorBG = enumPreference("pref_card_color_bg", ColorSelectionBG.SOLID_GREY_DARK)

		var focusBorderSize = enumPreference("pref_focus_border_size", FocusBorderSize.NORMAl)

		var focusBorderColor = enumPreference("pref_focus_border_color", ColorSelection.JELLYFIN_BLUE)

		var focusIconSize = enumPreference("pref_focus_icon_size", FocusIconSize.MED)

		var cardCornerRounding = intPreference("pref_card_corner_rounding", 8)

/* Audio, Subtitles */
		/**
		 * Enable subtitles background
		 */
		var subtitlesBackgroundEnabled = booleanPreference("subtitles_background_enabled", true)

		/**
		 * Set default subtitles font size
		 */
		var defaultSubtitlesSize = intPreference("subtitles_size", 28)

		/**
		 * Subtitle Language
		 */
		var subtitleLanguage = enumPreference("pref_subtitle_language", LanguagesSubtitle.AUTO)

		/**
		 * Audio Language
		 */
		var audioLanguage = enumPreference("pref_audio_language", LanguagesAudio.AUTO)

		/**
		 * DTS capable audio device
		 */
		var dtsCapableDevice = booleanPreference("pref_dts_capable_device", true)

		/**
		 * Force bitstream compliant surround codecs
		 */
		var forceBitstreamCompliantSurroundCodecs = booleanPreference("pref_force_bitstream_compliant_surround_codecs", false)

		/**
		 * Forced audio codec
		 */
		var forcedAudioCodec = enumPreference("pref_forced_audio_codec", AudioCodecOut.NONE)

		/**
		 * Force Stereo audio
		 */
		var forceStereo = booleanPreference("pref_force_stereo_audio", false)

		/**
		 * No forced subtitles
		 */
		var noForcedSubtitles = booleanPreference("pref_no_forced_subtitles", false)

		/**
		 * Allow same language subtitles
		 */
		var allowSameLanguageSubs = booleanPreference("pref_allow_same_language_subs", false)

		/**
		 * Use SDH subtitles
		 */
		var useSdhSubtitles = booleanPreference("pref_use_sdh_subtitles", false)

/* App Behaviour */
		/**
		 * Clear audio queue on App exit.
		 */
		var clearAudioQueueOnExit = booleanPreference("pref_clear_audio_queue_on_exit", false)
	}

//	init {
//		// Note: Create a single migration per app version
//		// Note: Migrations are never executed for fresh installs
//		runMigrations {
//			// v0.13.3 to v0.13.4
//			migration(toVersion = 6) {
//				putEnum("refresh_rate_switching_behavior",
//					when {
//						it.getBoolean("pref_refresh_switching", false) -> RefreshRateSwitchingBehavior.SCALE_ON_TV
//						else -> RefreshRateSwitchingBehavior.DISABLED
//					})
//			}
//		}
//	}

	fun getUiSettingsHash(): Int {
		return this[appTheme].hashCode() +
				this[backdropType].hashCode() +
				this[backdropBlur].hashCode() +
				this[premieresEnabled].hashCode() +
				this[clockBehavior].hashCode() +
				this[defaultRatingType].hashCode() +
				this[hideParentalRatings].hashCode() +
				this[watchedIndicatorBehavior].hashCode() +
				this[focusZoomSize].hashCode() +
				this[focusBorderSize].hashCode() +
				this[focusBorderColor].hashCode() +
				this[focusIconSize].hashCode() +
				this[enableFocusDimming].hashCode() +
				this[cardCornerRounding].hashCode() +
				this[cardSpacing].hashCode() +
				this[cardColorBG].hashCode()
	}
}
