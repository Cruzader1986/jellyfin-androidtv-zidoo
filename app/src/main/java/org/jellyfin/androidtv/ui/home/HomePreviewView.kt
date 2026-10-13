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
import org.jellyfin.androidtv.util.LayoutHelper
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
 * Large preview of the focused card at the top of the modern home screen: logo or title, year,
 * rating, runtime, quality chips (4K, Dolby Vision, Atmos…), overview and resume progress.
 * The backdrop behind it comes from the BackgroundService, which already follows the focus.
 */
class HomePreviewView(context: Context) : FrameLayout(context), KoinComponent {
	private val api by inject<ApiClient>()

	private val density = resources.displayMetrics.density
	private fun dp(value: Int) = (value * density).toInt()

	private val logo = ImageView(context).apply {
		scaleType = ImageView.ScaleType.FIT_START
		adjustViewBounds = true
	}
	private val title = TextView(context).apply {
		textSize = 34f
		setTypeface(Typeface.create("sans-serif", Typeface.BOLD))
		setTextColor(Color.WHITE)
		maxLines = 2
		ellipsize = TextUtils.TruncateAt.END
		setShadowLayer(8f, 0f, 2f, Color.BLACK)
	}
	private val subtitle = TextView(context).apply {
		textSize = 20f
		setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
		setTextColor(Color.rgb(224, 164, 88))
		maxLines = 1
		ellipsize = TextUtils.TruncateAt.END
	}
	private val meta = TextView(context).apply {
		textSize = 15f
		setTextColor(Color.rgb(201, 208, 220))
		maxLines = 1
	}
	private val chips = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
	private val overview = TextView(context).apply {
		textSize = 15f
		setTextColor(Color.rgb(215, 220, 229))
		maxLines = 2
		ellipsize = TextUtils.TruncateAt.END
		setLineSpacing(0f, 1.2f)
	}
	private val progressTrack = FrameLayout(context).apply {
		background = GradientDrawable().apply { cornerRadius = dp(2).toFloat(); setColor(Color.argb(60, 255, 255, 255)) }
	}
	private val progressFill = View(context).apply {
		background = GradientDrawable().apply { cornerRadius = dp(2).toFloat(); setColor(Color.WHITE) }
	}
	private val progressText = TextView(context).apply {
		textSize = 14f
		setTextColor(Color.rgb(174, 182, 196))
	}
	private val progressRow = LinearLayout(context).apply {
		orientation = LinearLayout.HORIZONTAL
		gravity = Gravity.CENTER_VERTICAL
	}

	private var scope: LifecycleCoroutineScope? = null
	private var job: Job? = null
	private var currentId: String? = null

	init {
		// Readable text over any backdrop: dark on the left and at the bottom
		addView(View(context).apply {
			background = GradientDrawable(
				GradientDrawable.Orientation.LEFT_RIGHT,
				intArrayOf(Color.argb(235, 7, 9, 13), Color.argb(170, 7, 9, 13), Color.argb(0, 7, 9, 13))
			)
		}, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
		addView(View(context).apply {
			background = GradientDrawable(
				GradientDrawable.Orientation.TOP_BOTTOM,
				intArrayOf(Color.argb(0, 7, 9, 13), Color.argb(255, 7, 9, 13))
			)
		}, LayoutParams(LayoutParams.MATCH_PARENT, dp(120), Gravity.BOTTOM))

		progressTrack.addView(progressFill, LayoutParams(0, LayoutParams.MATCH_PARENT))
		progressRow.addView(progressTrack, LinearLayout.LayoutParams(dp(180), dp(4)).apply { marginEnd = dp(12) })
		progressRow.addView(progressText)

		val column = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			gravity = Gravity.BOTTOM
			setPadding(HomeSideRail.COLLAPSED_WIDTH_DP.let { dp(it) } + dp(48), LayoutHelper.getToolbarHeight(), 0, dp(44))
			addView(logo, LinearLayout.LayoutParams(dp(300), dp(90)).apply { bottomMargin = dp(12) })
			addView(title, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
			addView(subtitle, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
			addView(meta, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
			addView(chips, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
			addView(overview, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
			addView(progressRow, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
		}
		addView(column, LayoutParams((resources.displayMetrics.widthPixels * 0.55f).toInt(), LayoutParams.MATCH_PARENT, Gravity.START or Gravity.BOTTOM))
		clear()
	}

	fun attach(lifecycleScope: LifecycleCoroutineScope) {
		scope = lifecycleScope
	}

	fun clear() {
		job?.cancel()
		currentId = null
		logo.visibility = GONE
		title.text = ""
		subtitle.visibility = GONE
		meta.text = ""
		chips.removeAllViews()
		overview.text = ""
		progressRow.visibility = GONE
	}

	/** Show the item with this id; details are loaded after a short pause so fast scrolling stays smooth. */
	fun show(itemId: String?, fallbackName: String?) {
		if (itemId == null || itemId == currentId) return
		currentId = itemId
		job?.cancel()

		val cached = cache.get(itemId)
		if (cached != null) {
			bind(cached)
			return
		}

		// Quick text while loading
		logo.visibility = GONE
		title.visibility = VISIBLE
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

		// Logo: the item's own, or the series' one for episodes
		val logoUrl = item.imageTags?.get(ImageType.LOGO)?.let { logoUrl(item.id, it) }
			?: item.parentLogoItemId?.let { id -> item.parentLogoImageTag?.let { logoUrl(id, it) } }
		if (logoUrl != null) {
			logo.visibility = VISIBLE
			title.visibility = GONE
			Glide.with(this).load(logoUrl).into(logo)
		} else {
			logo.visibility = GONE
			title.visibility = VISIBLE
			title.text = if (isEpisode) item.seriesName ?: item.name else item.name
		}

		if (isEpisode) {
			subtitle.visibility = VISIBLE
			subtitle.text = listOfNotNull(
				if (item.parentIndexNumber != null && item.indexNumber != null) "S${item.parentIndexNumber}:E${item.indexNumber}" else null,
				item.name,
			).joinToString("  ·  ")
		} else {
			subtitle.visibility = GONE
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
			progressText.text = "${formatDuration(runtimeMs - positionMs)} left"
		} else {
			progressRow.visibility = GONE
		}
	}

	private fun logoUrl(id: UUID, tag: String) =
		api.imageApi.getItemImageUrl(itemId = id, imageType = ImageType.LOGO, tag = tag, maxHeight = dp(90) * 2)

	private fun chip(label: String) = TextView(context).apply {
		text = label
		textSize = 12f
		setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
		setTextColor(Color.WHITE)
		letterSpacing = 0.05f
		setPadding(dp(8), dp(3), dp(8), dp(3))
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
		fun height(context: Context) = (context.resources.displayMetrics.heightPixels * 0.5f).toInt()
	}
}
