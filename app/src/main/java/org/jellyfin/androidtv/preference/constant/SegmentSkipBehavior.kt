package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.preference.dsl.EnumDisplayOptions

/**
 * Automatically skip Jellyfin media segments while the Zidoo (external) player is playing.
 */
enum class SegmentSkipBehavior {
	@EnumDisplayOptions(R.string.lbl_segment_skip_off)
	OFF,

	@EnumDisplayOptions(R.string.lbl_segment_skip_intro)
	INTRO,

	@EnumDisplayOptions(R.string.lbl_segment_skip_intro_credits)
	INTRO_AND_CREDITS,
}
