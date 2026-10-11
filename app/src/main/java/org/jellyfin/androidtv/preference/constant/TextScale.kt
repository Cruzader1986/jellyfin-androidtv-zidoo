package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.preference.dsl.EnumDisplayOptions

/**
 * App wide text size, multiplied with the system font scale (Andy2244/jellyfin-androidtv-zidoo#8).
 */
enum class TextScale(val factor: Float) {
	@EnumDisplayOptions(R.string.lbl_text_scale_normal)
	NORMAL(1.0f),

	@EnumDisplayOptions(R.string.lbl_text_scale_large)
	LARGE(1.15f),

	@EnumDisplayOptions(R.string.lbl_text_scale_extra_large)
	EXTRA_LARGE(1.3f),
}
