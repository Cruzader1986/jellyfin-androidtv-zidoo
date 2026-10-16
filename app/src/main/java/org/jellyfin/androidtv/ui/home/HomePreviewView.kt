package org.jellyfin.androidtv.ui.home

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Large preview of the focused card at the top of the modern home screen: title, year,
 * rating, runtime, quality chips (4K, Dolby Vision, Atmos…), overview and resume progress.
 * The view covers the whole screen (behind the rows) so the 16:9 backdrop is shown uncropped; the
 * details only use the top [height] of it.
 */
class HomePreviewView(context: Context) : FrameLayout(context), KoinComponent {
	private val api by inject<ApiClient>()

	private val density = resources.displayMetrics.density
	private fun dp(value: Int) = (value * density).toInt()

	private val backdrop = ImageView(context).apply {
		scaleType = ImageView.ScaleType.CENTER_CROP
	}
	private val title = TextView(context).apply {
		textSize = 24f
		setTypeface(Typeface.create("sans-serif", Typeface.BOLD))
		setTextColor(Color.WHITE)
		maxLines = 1
		ellipsize = TextUtils.TruncateAt.END
		setShadowLayer(8f, 0f, 2f, Color.BLACK)
	}
	private val subtitle = TextView(context).apply {
		setShadowLayer(6f, 0f, 1f, Color.BLACK)
		textSize = 17f
		setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
		setTextColor(Color.rgb(224, 164, 88))
		maxLines = 1
		ellipsize = TextUtils.TruncateAt.END
	}
	private val meta = TextView(context).apply {
		setShadowLayer(6f, 0f, 1f, Color.BLACK)
		textSize = 13f
		setTextColor(Color.rgb(201, 208, 220))
		maxLines = 1
	}
	private val chips = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
	private val overview = TextView(context).apply {
		setShadowLayer(6f, 0f, 1f, Color.BLACK)
		textSize = 12f
		setTextColor(Color.rgb(215, 220, 229))
		ellipsize = TextUtils.TruncateAt.END
		setLineSpacing(0f, 1.15f)
		// Fill all the room down to the bottom of the preview: as many whole lines as fit, then "…"
		addOnLayoutChangeListener { view, _, top, _, bottom, _, _, _, _ ->
			val text = view as TextView
			val lines = ((bottom - top - text.paddingTop - text.paddingBottom) / text.lineHeight).coerceAtLeast(4)
			if (text.maxLines != lines) text.post { text.maxLines = lines }
		}
	}
	private val progressTrack = FrameLayout(context).apply {
		background = GradientDrawable().apply { cornerRadius = dp(2).toFloat(); setColor(Color.argb(60, 255, 255, 255)) }
	}
	private val progressFill = View(context).apply {
		background = GradientDrawable().apply { cornerRadius = dp(2).toFloat(); setColor(Color.WHITE) }
	}
	private val playedText = TextView(context).apply {
		textSize = 12f
		setTextColor(Color.rgb(174, 182, 196))
		setShadowLayer(6f, 0f, 1f, Color.BLACK)
	}
	private val progressText = TextView(context).apply {
		textSize = 12f
		setTextColor(Color.rgb(174, 182, 196))
	}
	private val progressRow = LinearLayout(context).apply {
		orientation = LinearLayout.HORIZONTAL
		gravity = Gravity.CENTER_VERTICAL
	}

	private var scope: LifecycleCoroutineScope? = null
	private var job: Job? = null
	private var currentId: String? = null
	private var currentBackdropUrl: String? = null
	private val previewHeight = height(context)
	// Room between the text and the edge of the dark panel behind it
	private val PANEL_PAD_H = dp(16)
	private val PANEL_PAD_V = dp(8)

	init {
		addView(backdrop, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
		// No tint over the details (the text has shadows), only a fade that darkens under the rows
		val fadeStart = previewHeight - dp(60)
		addView(View(context).apply {
			background = GradientDrawable(
				GradientDrawable.Orientation.TOP_BOTTOM,
				intArrayOf(Color.argb(0, 7, 9, 13), Color.argb(200, 7, 9, 13), Color.argb(225, 7, 9, 13))
			)
		}, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply { topMargin = fadeStart })

		progressTrack.addView(progressFill, LayoutParams(0, LayoutParams.MATCH_PARENT))
		progressRow.addView(playedText, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(10) })
		progressRow.addView(progressTrack, LinearLayout.LayoutParams(dp(180), dp(4)).apply { marginEnd = dp(10) })
		progressRow.addView(progressText)
		progressText.setShadowLayer(6f, 0f, 1f, Color.BLACK)

		val column = DetailsColumn().apply {
			orientation = LinearLayout.VERTICAL
			// Start right at the top (level with the clock), small padding next to the side menu
			gravity = Gravity.TOP
			// Right padding too, so the panel's right edge (and its rounded corners) stays inside the view
			setPadding(HomeSideRail.COLLAPSED_WIDTH_DP.let { dp(it) } + dp(10) + PANEL_PAD_H, dp(6) + PANEL_PAD_V, PANEL_PAD_H, dp(6) + PANEL_PAD_V)
			addView(title, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(2) })
			addView(subtitle, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
			addView(meta, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) })
			addView(chips, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
			// Takes whatever height is left under the details
			addView(overview, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
		}
		addView(column, LayoutParams((resources.displayMetrics.widthPixels * 0.55f).toInt(), previewHeight, Gravity.START or Gravity.TOP))
		// Resume progress sits in the lower right corner of the preview, so the description gets the room under the details
		addView(progressRow, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.BOTTOM).apply {
			marginEnd = dp(24)
			bottomMargin = resources.displayMetrics.heightPixels - previewHeight + dp(10)
		})
		clear()
	}

	/**
	 * The details column, with a dark see-through panel behind the text. The panel has a fixed size
	 * (the whole details area) so it stays put while switching titles instead of resizing or vanishing.
	 */
	private inner class DetailsColumn : LinearLayout(context) {
		private val panelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 7, 9, 13) }
		private val panelRect = android.graphics.RectF()
		private val radius = dp(12).toFloat()

		override fun dispatchDraw(canvas: android.graphics.Canvas) {
			panelRect.set(
				(paddingLeft - PANEL_PAD_H).toFloat(),
				(paddingTop - PANEL_PAD_V).toFloat(),
				(width - paddingRight + PANEL_PAD_H).toFloat(),
				(height - paddingBottom + PANEL_PAD_V).toFloat(),
			)
			canvas.drawRoundRect(panelRect, radius, radius, panelPaint)
			super.dispatchDraw(canvas)
		}
	}

	fun attach(lifecycleScope: LifecycleCoroutineScope) {
		scope = lifecycleScope
	}

	fun clear() {
		job?.cancel()
		currentId = null
		currentBackdropUrl = null
		Glide.with(this).clear(backdrop)
		title.text = ""
		subtitle.visibility = GONE
		meta.text = ""
		chips.removeAllViews()
		overview.text = ""
		progressRow.visibility = GONE
	}

	/**
	 * Show the item with this id; details are loaded after a short pause so fast scrolling stays smooth.
	 * The backdrop comes straight from [rowItem] (the card's own data), so it shows up right away.
	 */
	fun show(itemId: String?, fallbackName: String?, rowItem: BaseItemDto? = null) {
		if (itemId == null || itemId == currentId) return
		currentId = itemId
		job?.cancel()
		rowItem?.let { setBackdrop(it) }

		val cached = cache.get(itemId)
		if (cached != null) {
			bind(cached)
			return
		}

		// Quick text while loading
		title.text = fallbackName.orEmpty()
		subtitle.visibility = GONE
		meta.text = ""
		chips.removeAllViews()
		overview.text = ""
		progressRow.visibility = GONE

		val uuid = itemId.toUUIDOrNull() ?: return
		job = scope?.launch {
			delay(250)
			val item = withContext(Dispatchers.IO) { runCatching { api.userLibraryApi.getItem(itemId = uuid).content }.getOrNull() }
				?: return@launch
			cache.put(itemId, item)
			if (currentId == itemId) bind(item)
		}
	}

	private fun bind(item: BaseItemDto) {
		val isEpisode = item.type == BaseItemKind.EPISODE

		setBackdrop(item)

		// Plain title instead of the logo artwork, leaves more room for the details.
		// Episodes get "S1:E3 · Name" on the same line, smaller and in the accent color, instead of a line of their own.
		subtitle.visibility = GONE
		if (isEpisode) {
			val episode = listOfNotNull(
				if (item.parentIndexNumber != null && item.indexNumber != null) "S${item.parentIndexNumber}:E${item.indexNumber}" else null,
				item.name,
			).joinToString("  ·  ")
			title.text = android.text.SpannableStringBuilder(item.seriesName ?: item.name.orEmpty()).apply {
				if (episode.isNotEmpty()) {
					append("   ")
					val start = length
					append(episode)
					setSpan(android.text.style.AbsoluteSizeSpan(17, true), start, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
					setSpan(android.text.style.ForegroundColorSpan(Color.rgb(224, 164, 88)), start, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
					setSpan(android.text.style.StyleSpan(Typeface.NORMAL), start, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
			}
		} else {
			title.text = item.name
		}

		val runtimeMs = item.runTimeTicks?.div(10_000)
		val positionMs = item.userData?.playbackPositionTicks?.div(10_000) ?: 0L
		meta.text = listOfNotNull(
			item.productionYear?.toString(),
			item.officialRating,
			runtimeMs?.takeIf { it > 0 }?.let { formatDuration(it) },
			item.communityRating?.let { String.format(Locale.ROOT, "★ %.1f", it) },
			runtimeMs?.takeIf { it > 0 }?.let {
				DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis() + it - positionMs))
			}?.let { "Ends at $it" },
		).joinToString("   •   ")

		chips.removeAllViews()
		qualityChips(item).forEach { label -> chips.addView(chip(label), LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) }) }
		chips.visibility = if (chips.childCount > 0) VISIBLE else GONE

		overview.text = item.overview.orEmpty()

		if (runtimeMs != null && runtimeMs > 0 && positionMs > 0) {
			progressRow.visibility = VISIBLE
			val fraction = (positionMs.toFloat() / runtimeMs).coerceIn(0f, 1f)
			(progressFill.layoutParams as LayoutParams).width = (dp(180) * fraction).toInt()
			progressFill.requestLayout()
			playedText.text = formatDuration(positionMs)
			progressText.text = "${formatDuration(runtimeMs - positionMs)} left"
		} else {
			progressRow.visibility = GONE
		}
	}

	/** Backdrop: the item's own, else its series' / parent's. Skipped when it is already showing. */
	private fun setBackdrop(item: BaseItemDto) {
		val url = item.backdropImageTags?.firstOrNull()?.let { backdropUrl(item.id, it) }
			?: item.parentBackdropItemId?.let { id -> item.parentBackdropImageTags?.firstOrNull()?.let { backdropUrl(id, it) } }
			?: return // nothing in this data, keep what is there (the full item may still have one)
		if (url == currentBackdropUrl) return
		currentBackdropUrl = url
		Glide.with(this).load(url)
			.transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(250))
			.into(backdrop)
	}

	private fun backdropUrl(id: UUID, tag: String) =
		api.imageApi.getItemImageUrl(itemId = id, imageType = ImageType.BACKDROP, tag = tag, maxWidth = resources.displayMetrics.widthPixels, quality = 85)

	private fun chip(label: String) = TextView(context).apply {
		text = label
		setShadowLayer(6f, 0f, 1f, Color.BLACK)
		textSize = 11f
		setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
		setTextColor(Color.WHITE)
		letterSpacing = 0.05f
		setPadding(dp(7), dp(2), dp(7), dp(2))
		background = GradientDrawable().apply {
			cornerRadius = dp(5).toFloat()
			setStroke(dp(1), Color.argb(130, 255, 255, 255))
		}
	}

	private fun qualityChips(item: BaseItemDto): List<String> {
		val streams = item.mediaSources?.firstOrNull()?.mediaStreams.orEmpty()
		val video = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
		val audio = streams.filter { it.type == MediaStreamType.AUDIO }.let { list -> list.firstOrNull { it.isDefault } ?: list.firstOrNull() }
		val result = mutableListOf<String>()

		video?.width?.let { width ->
			when {
				width >= 3800 -> result += "4K"
				width >= 1900 -> result += "1080P"
				width >= 1260 -> result += "720P"
			}
		}
		if (item.video3dFormat != null) result += "3D"
		val range = video?.videoRangeType.orEmpty().uppercase(Locale.ROOT)
		when {
			range.contains("DOVI") -> result += "DOLBY VISION"
			range.contains("HDR10PLUS") -> result += "HDR10+"
			range.contains("HDR10") || range == "HDR" -> result += "HDR10"
			range.contains("HLG") -> result += "HLG"
			video?.videoRange.equals("HDR", ignoreCase = true) -> result += "HDR"
		}

		if (audio != null) {
			val text = listOfNotNull(audio.profile, audio.displayTitle).joinToString(" ").uppercase(Locale.ROOT)
			val codec = audio.codec.orEmpty().lowercase(Locale.ROOT)
			when {
				text.contains("ATMOS") -> result += "ATMOS"
				text.contains("DTS:X") || text.contains("DTS-X") -> result += "DTS:X"
				codec == "truehd" -> result += "TRUEHD"
				codec == "dts" && (text.contains("MA") || text.contains("HD")) -> result += "DTS-HD"
				codec == "dts" -> result += "DTS"
				codec == "eac3" -> result += "DD+"
				codec == "ac3" -> result += "DOLBY DIGITAL"
			}
			when (audio.channels) {
				8 -> result += "7.1"
				6 -> result += "5.1"
			}
		}
		return result
	}

	private fun formatDuration(ms: Long): String {
		val minutes = ms / 60_000
		return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
	}

	companion object {
		private val cache = LruCache<String, BaseItemDto>(60)

		/** Height of the preview area; the home rows start below it. */
		@JvmStatic
		fun height(context: Context) = (context.resources.displayMetrics.heightPixels * 0.35f).toInt()
	}
}
