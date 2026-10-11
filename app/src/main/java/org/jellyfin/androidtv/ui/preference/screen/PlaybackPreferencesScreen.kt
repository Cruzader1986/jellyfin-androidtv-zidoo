package org.jellyfin.androidtv.ui.preference.screen

import android.app.AlertDialog
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.*
import org.jellyfin.androidtv.ui.preference.custom.DurationSeekBarPreference
import org.jellyfin.androidtv.ui.preference.dsl.*
import org.jellyfin.androidtv.util.DeviceUtils
import org.jellyfin.androidtv.util.TimeUtils
import org.koin.android.ext.android.inject

class PlaybackPreferencesScreen : OptionsFragment() {
	private val userPreferences: UserPreferences by inject()

	override val screen by optionsScreen {
		setTitle(R.string.pref_playback)

		category {
			setTitle(R.string.pref_customization)

			enum<NextUpBehavior> {
				setTitle(R.string.pref_next_up_behavior_title)
				bind(userPreferences, UserPreferences.nextUpBehavior)
				depends { userPreferences[UserPreferences.mediaQueuingEnabled] }
			}

			@Suppress("MagicNumber")
			seekbar {
				setTitle(R.string.pref_next_up_timeout_title)
				setContent(R.string.pref_next_up_timeout_summary)
				min = 0 // value of 0 disables timer
				max = 30_000
				increment = 1_000
				valueFormatter = object : DurationSeekBarPreference.ValueFormatter() {
					override fun display(value: Int): String = when (value) {
						NEXTUP_TIMER_DISABLED -> getString(R.string.pref_next_up_timeout_disabled)
						else -> "${value / 1000}s"
					}
				}
				bind(userPreferences, UserPreferences.nextUpTimeout)
				depends {
					userPreferences[UserPreferences.mediaQueuingEnabled]
						&& userPreferences[UserPreferences.nextUpBehavior] != NextUpBehavior.DISABLED
				}
			}

			@Suppress("MagicNumber")
			list {
				setTitle(R.string.lbl_resume_preroll)
				entries = setOf(
					0, // Disable
					3, 5, 7, // 10<
					10, 20, 30, 60, // 100<
					120, 300
				).associate {
					val value = if (it == 0) getString(R.string.lbl_none)
					else TimeUtils.formatSeconds(context, it)

					it.toString() to value
				}
				bind(userPreferences, UserPreferences.resumeSubtractDuration)
			}

			checkbox {
				setTitle(R.string.lbl_tv_queuing)
				setContent(R.string.sum_tv_queuing)
				bind(userPreferences, UserPreferences.mediaQueuingEnabled)
			}

			checkbox {
				setTitle(R.string.pref_clear_audio_queue_on_exit)
				bind(userPreferences, UserPreferences.clearAudioQueueOnExit)
			}

//			checkbox {
//				setTitle(R.string.lbl_enable_cinema_mode)
//				setContent(R.string.sum_enable_cinema_mode)
//				bind(userPreferences, UserPreferences.cinemaModeEnabled)
//				depends { userPreferences[UserPreferences.videoPlayer] != PreferredVideoPlayer.EXTERNAL }
//			}
		}

		category {
			setTitle(R.string.pref_video)

//			enum<PreferredVideoPlayer> {
//				setTitle(R.string.pref_media_player)
//				bind(userPreferences, UserPreferences.videoPlayer)
//			}

			@Suppress("MagicNumber")
			list {
				setTitle(R.string.pref_max_bitrate_title)
				entries = setOf(
//					0.0, // auto
					150.0, 140.0, 130.0, 120.0, 110.0, 100.0, // 100 >=
					90.0, 80.0, 70.0, 60.0, 50.0, 40.0, 30.0, 21.0, 15.0, 10.0, // 10 >=
					5.0, 3.0, 2.0, 1.0, // 1 >=
					0.72, 0.42 // 0 >=
				).associate {
					val value = when {
						it == 0.0 -> getString(R.string.bitrate_auto)
						it >= 1.0 -> getString(R.string.bitrate_mbit, it)
						else -> getString(R.string.bitrate_kbit, it * 100.0)
					}

					it.toString().removeSuffix(".0") to value
				}
				bind(userPreferences, UserPreferences.maxBitrate)
			}

			checkbox {
				setTitle(R.string.pref_use_direct_path_title)
				setContent(R.string.pref_use_direct_path_summary)
				bind {
					get { userPreferences[UserPreferences.externalVideoPlayerSendPath] }
					set {
						if (it) {
							AlertDialog.Builder(activity)
								.setTitle(R.string.lbl_warning)
								.setMessage(if (DeviceUtils.hasNewZidooApi()) R.string.msg_external_path else R.string.msg_external_path_legacy)
								.setPositiveButton(R.string.btn_got_it, null)
								.show()
						}

						userPreferences[UserPreferences.externalVideoPlayerSendPath] = it
					}
					default { userPreferences.getDefaultValue(UserPreferences.externalVideoPlayerSendPath) }
				}
				depends { userPreferences[UserPreferences.videoPlayer] == PreferredVideoPlayer.EXTERNAL }
			}

			enum<SegmentSkipBehavior> {
				setTitle(R.string.pref_segment_skip_title)
				bind(userPreferences, UserPreferences.segmentSkipBehavior)
				depends { userPreferences[UserPreferences.videoPlayer] == PreferredVideoPlayer.EXTERNAL }
			}

			checkbox {
				setTitle(R.string.lbl_allow_transcode_fallback)
				setContent(R.string.desc_allow_transcode_fallback)
				bind(userPreferences, UserPreferences.enableTranscodingFallback)
				depends { userPreferences[UserPreferences.externalVideoPlayerSendPath] }
			}

//			enum<RefreshRateSwitchingBehavior> {
//				setTitle(R.string.lbl_refresh_switching)
//				bind(userPreferences, UserPreferences.refreshRateSwitchingBehavior)
//				depends { DeviceUtils.is60() && userPreferences[UserPreferences.videoPlayer] != PreferredVideoPlayer.EXTERNAL }
//			}
		}

		category {
			setTitle(R.string.pref_audio)

			enum<LanguagesAudio> {
				setTitle(R.string.pref_languages_audio)
				bind(userPreferences, UserPreferences.audioLanguage)
			}

			checkbox {
				setTitle(R.string.lbl_dts_enabled_device)
				setContent(R.string.desc_dts_enabled_device)
				bind(userPreferences, UserPreferences.dtsCapableDevice)
			}

//			enum<AudioBehavior> {
//				setTitle(R.string.lbl_audio_output)
//				bind(userPreferences, UserPreferences.audioBehaviour)
//				depends { userPreferences[UserPreferences.videoPlayer] != PreferredVideoPlayer.EXTERNAL }
//			}
//
//			checkbox {
//				setTitle(R.string.lbl_bitstream_ac3)
//				setContent(R.string.desc_bitstream_ac3)
//				bind(userPreferences, UserPreferences.ac3Enabled)
//				depends { userPreferences[UserPreferences.videoPlayer] != PreferredVideoPlayer.EXTERNAL && !DeviceUtils.is60() }
//			}
//
//			checkbox {
//				setTitle(R.string.lbl_bitstream_dts)
//				setContent(R.string.desc_bitstream_ac3)
//				bind(userPreferences, UserPreferences.dtsEnabled)
//				depends { userPreferences[UserPreferences.videoPlayer] != PreferredVideoPlayer.EXTERNAL }
//			}
//
//			@Suppress("MagicNumber")
//			seekbar {
//				setTitle(R.string.pref_libvlc_audio_delay_title)
//				min = -5_000
//				max = 5_000
//				valueFormatter = object : DurationSeekBarPreference.ValueFormatter() {
//					override fun display(value: Int) = "${value}ms"
//				}
//				bind(userPreferences, UserPreferences.libVLCAudioDelay)
//			}
		}

		category {
			setTitle(R.string.pref_subtitles)

			enum<LanguagesSubtitle> {
				setTitle(R.string.pref_languages_subtitle)
				bind(userPreferences, UserPreferences.subtitleLanguage)
				depends { userPreferences[UserPreferences.audioLanguage] != LanguagesAudio.DEVICE }
			}

			checkbox {
				setTitle(R.string.lbl_no_forced_subs)
				setContent(R.string.desc_no_forced_subs)
				bind(userPreferences, UserPreferences.noForcedSubtitles)
				depends { userPreferences[UserPreferences.audioLanguage] != LanguagesAudio.DEVICE }
			}

			checkbox {
				setTitle(R.string.lbl_allow_same_lang_subs)
				setContent(R.string.desc_allow_same_lang_subs)
				bind(userPreferences, UserPreferences.allowSameLanguageSubs)
				depends { userPreferences[UserPreferences.audioLanguage] != LanguagesAudio.DEVICE }
			}

			checkbox {
				setTitle(R.string.lbl_use_sdh_subs)
				setContent(R.string.desc_use_sdh_subs)
				bind(userPreferences, UserPreferences.useSdhSubtitles)
				depends { userPreferences[UserPreferences.audioLanguage] != LanguagesAudio.DEVICE }
			}

//			checkbox {
//				setTitle(R.string.pref_subtitles_background_title)
//				setContent(R.string.pref_subtitles_background_summary)
//				bind(userPreferences, UserPreferences.subtitlesBackgroundEnabled)
//			}

//			@Suppress("MagicNumber")
//			seekbar {
//				setTitle(R.string.pref_libvlc_subtitle_delay_title)
//				min = -50_000
//				max = 50_000
//				valueFormatter = object : DurationSeekBarPreference.ValueFormatter() {
//					override fun display(value: Int) = "${value}ms"
//				}
//				bind(userPreferences, UserPreferences.libVLCSubtitleDelay)
//			}
//
//			@Suppress("MagicNumber")
//			seekbar {
//				setTitle(R.string.pref_subtitles_size)
//				min = 10
//				max = 38
//				bind(userPreferences, UserPreferences.defaultSubtitlesSize)
//			}
		}

//		category {
//			setTitle(R.string.pref_live_tv_cat)
//
//			enum<PreferredVideoPlayer> {
//				setTitle(R.string.pref_media_player)
//				bind(userPreferences, UserPreferences.liveTvVideoPlayer)
//			}
//
//			checkbox {
//				setTitle(R.string.lbl_direct_stream_live)
//				bind(userPreferences, UserPreferences.liveTvDirectPlayEnabled)
//				setContent(R.string.pref_direct_stream_live_on, R.string.pref_direct_stream_live_off)
//			}
//		}
	}
}
