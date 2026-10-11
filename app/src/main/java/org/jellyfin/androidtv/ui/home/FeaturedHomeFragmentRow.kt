package org.jellyfin.androidtv.ui.home

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.LifecycleCoroutineScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.ui.itemdetail.FullDetailsActivity
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.get
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.Locale

/** One featured movie or series shown in the home banner. */
data class FeaturedItem(
	val id: String,
	val name: String,
	val overview: String?,
	val meta: String,
	val backdropUrl: String,
	val logoUrl: String?,
)

/** Leanback row holding the featured items, rendered by [FeaturedRowPresenter]. */
class FeaturedRow(val items: List<FeaturedItem>) : Row()

/**
 * Rotating banner at the top of the home screen with random movies and shows: backdrop, logo,
 * year/rating/genres and overview. Left/right switches items, OK opens the details screen.
 */
class FeaturedHomeFragmentRow(
	private val lifecycleScope: LifecycleCoroutineScope,
	private val onAdded: () -> Unit = {},
) : HomeFragmentRow, KoinComponent {
	private val api by inject<ApiClient>()
	private val userRepository by inject<UserRepository>()

	override fun addToRowsAdapter(context: Context, presenter: CardPresenter, rowsAdapter: ArrayObjectAdapter) {
		lifecycleScope.launch {
			val items = withContext(Dispatchers.IO) { load() }
			if (items.isEmpty()) return@launch
			(0 until rowsAdapter.size()).map { rowsAdapter.get(it) }.filterIsInstance<FeaturedRow>().forEach { rowsAdapter.remove(it) }
			rowsAdapter.add(0, FeaturedRow(items))
			onAdded()
		}
	}

	private suspend fun load(): List<FeaturedItem> = try {
		val userId = userRepository.currentUser.value?.id?.toString()
		val baseUrl = api.baseUrl?.trimEnd('/').orEmpty()
		val result = api.get<JsonObject>("/Items", queryParameters = mapOf(
			"userId" to userId,
			"includeItemTypes" to "Movie,Series",
			"recursive" to true,
			"sortBy" to "Random",
			"imageTypes" to "Backdrop",
			"hasOverview" to true,
			"isPlayed" to false,
			"fields" to "Overview,Genres",
			"limit" to 10,
		)).content

		result["Items"]?.jsonArray.orEmpty().mapNotNull { element ->
			val json = element.jsonObject
			fun string(key: String) = json[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

			val id = string("Id") ?: return@mapNotNull null
			val backdropTag = json["BackdropImageTags"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
			val logoTag = json["ImageTags"]?.jsonObject?.get("Logo")?.jsonPrimitive?.contentOrNull
			val genres = json["Genres"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.take(3).orEmpty()
			val rating = json["CommunityRating"]?.jsonPrimitive?.doubleOrNull

			FeaturedItem(
				id = id,
				name = string("Name") ?: return@mapNotNull null,
				overview = string("Overview"),
				meta = listOfNotNull(
					string("ProductionYear"),
					string("OfficialRating"),
					rating?.let { String.format(Locale.ROOT, "★ %.1f", it) },
					genres.joinToString(", ").takeIf { it.isNotEmpty() },
				).joinToString("   ·   "),
				backdropUrl = "$baseUrl/Items/$id/Images/Backdrop/0?tag=$backdropTag&maxWidth=1280&quality=80",
				logoUrl = logoTag?.let { "$baseUrl/Items/$id/Images/Logo?tag=$it&maxWidth=700&quality=90" },
			)
		}
	} catch (err: Exception) {
		Timber.w(err, "Unable to load featured items")
		emptyList()
	}
}

class FeaturedRowPresenter : RowPresenter() {
	init {
		headerPresenter = null
		selectEffectEnabled = false
	}

	private class BannerView(context: Context) : FrameLayout(context) {
		private val density = resources.displayMetrics.density
		private fun dp(value: Int) = (value * density).toInt()

		val backdrop = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
		val logo = ImageView(context).apply {
			scaleType = ImageView.ScaleType.FIT_START
			adjustViewBounds = true
		}
		val title = TextView(context).apply {
			textSize = 34f
			setTypeface(typeface, Typeface.BOLD)
			setTextColor(Color.WHITE)
			maxLines = 2
			ellipsize = TextUtils.TruncateAt.END
		}
		val meta = TextView(context).apply {
			textSize = 16f
			setTextColor(Color.argb(220, 255, 255, 255))
			maxLines = 1
		}
		val overview = TextView(context).apply {
			textSize = 16f
			setTextColor(Color.argb(200, 255, 255, 255))
			maxLines = 3
			ellipsize = TextUtils.TruncateAt.END
		}
		val dots = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
		private val border = View(context).apply {
			background = GradientDrawable().apply {
				setStroke(dp(3), Color.WHITE)
				cornerRadius = dp(8).toFloat()
			}
			visibility = View.INVISIBLE
		}

		var items: List<FeaturedItem> = emptyList()
		var index = 0
		private val handler = Handler(Looper.getMainLooper())
		private val rotate = object : Runnable {
			override fun run() {
				show(index + 1)
				handler.postDelayed(this, ROTATE_INTERVAL_MS)
			}
		}

		init {
			isFocusable = true
			isFocusableInTouchMode = true
			clipToOutline = true
			background = GradientDrawable().apply { cornerRadius = dp(8).toFloat() }

			addView(backdrop, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
			// Darken the left side and the bottom so the text stays readable
			addView(View(context).apply {
				background = GradientDrawable(
					GradientDrawable.Orientation.LEFT_RIGHT,
					intArrayOf(Color.argb(235, 0, 0, 0), Color.argb(140, 0, 0, 0), Color.TRANSPARENT)
				)
			}, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

			val info = LinearLayout(context).apply {
				orientation = LinearLayout.VERTICAL
				gravity = Gravity.BOTTOM
				setPadding(dp(32), dp(24), dp(32), dp(20))
				addView(logo, LinearLayout.LayoutParams(dp(320), dp(110)).apply { bottomMargin = dp(10) })
				addView(title, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) })
				addView(meta, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
				addView(overview, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })
				addView(dots, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(8)))
			}
			addView(info, LayoutParams((resources.displayMetrics.widthPixels * 0.55f).toInt(), LayoutParams.MATCH_PARENT, Gravity.START or Gravity.BOTTOM))
			addView(border, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

			setOnFocusChangeListener { _, hasFocus -> border.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE }
			setOnKeyListener { _, keyCode, event ->
				if (event.action != KeyEvent.ACTION_DOWN || items.size < 2) return@setOnKeyListener false
				when (keyCode) {
					KeyEvent.KEYCODE_DPAD_RIGHT -> { show(index + 1); restartTimer(); true }
					KeyEvent.KEYCODE_DPAD_LEFT -> { show(index - 1); restartTimer(); true }
					else -> false
				}
			}
			setOnClickListener {
				val item = items.getOrNull(index) ?: return@setOnClickListener
				context.startActivity(Intent(context, FullDetailsActivity::class.java).putExtra("ItemId", item.id))
			}
		}

		fun show(newIndex: Int) {
			if (items.isEmpty()) return
			index = ((newIndex % items.size) + items.size) % items.size
			val item = items[index]

			Glide.with(this).load(item.backdropUrl).into(backdrop)
			if (item.logoUrl != null) {
				logo.visibility = View.VISIBLE
				title.visibility = View.GONE
				Glide.with(this).load(item.logoUrl).into(logo)
			} else {
				logo.visibility = View.GONE
				title.visibility = View.VISIBLE
				title.text = item.name
			}
			// Warm up the next backdrop so the rotation doesn't show an empty banner
			items.getOrNull((index + 1) % items.size)?.let { Glide.with(this).load(it.backdropUrl).preload() }
			meta.text = item.meta
			overview.text = item.overview.orEmpty()

			dots.removeAllViews()
			items.indices.forEach { i ->
				dots.addView(View(context).apply {
					background = GradientDrawable().apply {
						shape = GradientDrawable.OVAL
						setColor(if (i == index) Color.WHITE else Color.argb(90, 255, 255, 255))
					}
				}, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(6) })
			}
		}

		fun restartTimer() {
			handler.removeCallbacks(rotate)
			if (isAttachedToWindow && items.size > 1) handler.postDelayed(rotate, ROTATE_INTERVAL_MS)
		}

		override fun onAttachedToWindow() {
			super.onAttachedToWindow()
			restartTimer()
		}

		override fun onDetachedFromWindow() {
			handler.removeCallbacks(rotate)
			super.onDetachedFromWindow()
		}
	}

	override fun createRowViewHolder(parent: ViewGroup): ViewHolder {
		val res = parent.resources
		val height = (res.displayMetrics.heightPixels * BANNER_HEIGHT_FRACTION).toInt()
		val side = (48 * res.displayMetrics.density).toInt()
		val container = FrameLayout(parent.context).apply {
			// The home grid aligns rows under the toolbar, keep the banner below it
			setPadding(side, org.jellyfin.androidtv.util.LayoutHelper.getToolbarHeight(), side, (12 * res.displayMetrics.density).toInt())
			addView(BannerView(parent.context), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height))
		}
		return ViewHolder(container)
	}

	override fun onBindRowViewHolder(vh: ViewHolder, item: Any?) {
		super.onBindRowViewHolder(vh, item)
		val banner = (vh.view as ViewGroup).getChildAt(0) as BannerView
		banner.items = (item as? FeaturedRow)?.items.orEmpty()
		banner.show(0)
		banner.restartTimer()
	}

	override fun onUnbindRowViewHolder(vh: ViewHolder) {
		val banner = (vh.view as ViewGroup).getChildAt(0) as BannerView
		Glide.with(banner).clear(banner.backdrop)
		Glide.with(banner).clear(banner.logo)
		super.onUnbindRowViewHolder(vh)
	}

	companion object {
		private const val ROTATE_INTERVAL_MS = 9_000L
		private const val BANNER_HEIGHT_FRACTION = 0.42f
	}
}
