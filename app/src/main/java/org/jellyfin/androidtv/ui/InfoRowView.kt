package org.jellyfin.androidtv.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.PaintDrawable
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.AttributeSet
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.Space
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.carleslc.kotlinextensions.standard.isNotNull
import me.carleslc.kotlinextensions.time.milliseconds
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.RatingType
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.util.TimeUtils
import org.jellyfin.androidtv.util.apiclient.getProgramSubText
import org.jellyfin.androidtv.util.apiclient.getProgramUnknownChannelName
import org.jellyfin.androidtv.util.apiclient.isNew
import org.jellyfin.androidtv.util.dp
import org.jellyfin.androidtv.util.getColorFromAttribute
import org.jellyfin.androidtv.util.runtimeTicksToMs
import org.jellyfin.androidtv.util.saturationHSV
import org.jellyfin.androidtv.util.sdk.compat.asSdk
import org.jellyfin.androidtv.util.setMarginVertical
import org.jellyfin.androidtv.util.setPaddingHorizontal
import org.jellyfin.androidtv.util.sp
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SeriesStatus
import org.jellyfin.sdk.model.api.Video3dFormat
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import strings.isNotNullOrBlank
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale


class InfoRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : LinearLayoutAbs(context, attrs, defStyleAttr, defStyleRes), KoinComponent {

    private val userPreferences by inject<UserPreferences>()
    private val api by inject<ApiClient>()

    private var layoutTextSize: Int
    private val image_vMargin: Int
    private val blockText_sizeOffset: Int
    private val outlineText_sizeOffset: Int

    private val nf = NumberFormat.getInstance()
    private val nfp = NumberFormat.getPercentInstance()

    private var rowItem: BaseRowItem? = null
    private var baseItem: BaseItemDto? = null
    private var mediaSourceIdx: Int? = null
    private var isViewDirty = true

    var includeRuntime = false
    var includeEndtime = false

    companion object {
        private const val blockAlpha = 170
        @ColorInt val colorStatusRed = Color.argb(blockAlpha,128, 0,0)
        @ColorInt val colorStatusGreen = Color.argb(blockAlpha,0, 128,0)
        @ColorInt val colorMediaGolden = Color.HSVToColor(blockAlpha, floatArrayOf(50f,1.0f,0.7f))
        @ColorInt val colorMediaVideo = Color.HSVToColor(blockAlpha, floatArrayOf(20f,0.08f,0.6f))
        @ColorInt val colorMediaAudio = Color.HSVToColor(blockAlpha, floatArrayOf(170f,0.06f,0.6f))
        @ColorInt val colorGreyMid = Color.HSVToColor(blockAlpha, floatArrayOf(0f,0f,0.5f))

        // Items loaded through the legacy apiclient lose MediaStream.VideoRangeType. Cache the value per
        // media source id after fetching the item once with the SDK ("" = server did not provide one).
        private val videoRangeCache = LruCache<String, String>(500)
        private val pendingVideoRangeFetches = HashSet<String>()
    }

    init {
        context.obtainStyledAttributes(attrs, R.styleable.InfoRowView, defStyleAttr, defStyleRes).run {
            layoutTextSize = getDimensionPixelSize(R.styleable.InfoRowView_textSize, 16.dp(context))
            image_vMargin = getDimensionPixelSize(R.styleable.InfoRowView_imageMarginVertical, 1.dp(context))
            blockText_sizeOffset = getDimensionPixelSize(R.styleable.InfoRowView_blockText_sizeOffset, 2.dp(context))
            outlineText_sizeOffset = getDimensionPixelSize(R.styleable.InfoRowView_outlineText_sizeOffset, 0)
            recycle()
        }
    }

    fun setRowItem(rowItem: BaseRowItem?) {
        this.baseItem = null
        this.mediaSourceIdx = null
        this.rowItem = rowItem

        invalidate()
        doOnPreDraw { updateView() }
    }

    fun setBaseItem(baseItem: BaseItemDto?) {
        this.rowItem = null
        this.mediaSourceIdx = null
        this.baseItem = baseItem

        invalidate()
        doOnPreDraw { updateView() }
    }

    fun setMediaSourceIndex(idx: Int) {
        this.mediaSourceIdx = idx

        invalidate()
        doOnPreDraw { updateView() }
    }

    private fun updateView() {
        removeAllViews()
        if (measuredHeight > 0)
            layoutTextSize = measuredHeight.minus(measuredHeight * 0.15f).coerceAtLeast(12f).toInt() // NOTE we need some extra padding or text wont center

        if (rowItem.isNotNull() && rowItem?.isValid == true) {
            rowItem?.let {
                when {
                    it.isBaseItem && it.itemId.isNotNullOrBlank() -> buildDefaultInfoRow(it.baseItem.asSdk(), mediaSourceIdx)
                    it.isBaseItem && it.itemId.isNullOrBlank() -> addProgramChannel(it.baseItem.asSdk())
                    else -> addSubText(it)
                }
            }
        } else if (baseItem.isNotNull()) {
            baseItem?.let {
                if (it.id.isNotNull())
                    buildDefaultInfoRow(it, mediaSourceIdx)
                else
                    addProgramChannel(it)
            }
        }
        isViewDirty = false
    }

    private fun buildDefaultInfoRow(item: BaseItemDto, sourceIdx: Int?) {
        when (item.type) {
            BaseItemKind.EPISODE -> {
                addSeasonEpisode(item)
                addDate(item)
            }
            BaseItemKind.BOX_SET -> {
                addBoxSetCounts(item)
            }
            BaseItemKind.SERIES -> {
//                addSeasonCount(context, item, layout);
                if (SeriesStatus.CONTINUING.serialName.equals(item.status, ignoreCase = true)) {
                    addSeriesAirs(item)
                }
                addDate(item)
                includeEndtime = false
            }
            BaseItemKind.PROGRAM -> {
                addProgramInfo(item)
            }
            BaseItemKind.MUSIC_ARTIST -> {
                (item.albumCount ?: item.childCount)?.let {
                    addCount(it, if (it > 1) context.resources.getString(R.string.lbl_albums) else context.resources.getString(R.string.lbl_album))
                }
            }
            BaseItemKind.MUSIC_ALBUM -> {
                (item.albumArtist ?: item.artists?.getOrNull(0))?.let {
                    addText(it, maxWidth = 300)
                }
                addDate(item)
                (item.songCount ?: item.childCount)?.let {
                    addText("|")
                    addCount(it, if (it > 1) context.resources.getString(R.string.lbl_songs) else context.resources.getString(R.string.lbl_song))
                }
            }
            BaseItemKind.PLAYLIST -> {
                item.childCount?.let {
                    addCount(it, if (it > 1) context.resources.getString(R.string.lbl_items) else context.resources.getString(R.string.lbl_item))
                }
                item.cumulativeRunTimeTicks?.let {
                    addText("(" + TimeUtils.formatMillis(it.runtimeTicksToMs().toLong()) + ")", maxWidth = 300)
                }
            }
            else -> {
                addDate(item)
            }
        }

        if (includeRuntime)
            addRuntime(item)

        addSeriesStatus(item)

        if (!userPreferences[UserPreferences.hideParentalRatings])
            addParentalRating(item)

        if (userPreferences[UserPreferences.defaultRatingType] !== RatingType.RATING_HIDDEN)
            addCriticInfo(item)

        item.mediaSources?.getOrNull(sourceIdx ?: 0)?.takeUnless { it.mediaStreams.isNullOrEmpty() }?.let {
            addMediaDetails(it, item)
        }
    }

    private fun addBoxSetCounts(item: BaseItemDto) {
        var hasSpecificCounts = true
        when {
            item.movieCount != null -> addCount(item.movieCount!!, context.resources.getString(R.string.lbl_movies))
            item.seriesCount != null -> addCount(item.seriesCount!!, context.resources.getString(R.string.lbl_tv_series))
            else -> hasSpecificCounts = false
        }
        item.childCount?.takeIf { hasSpecificCounts }?.let {
            addCount(it, if (it > 1) context.resources.getString(R.string.lbl_items) else context.resources.getString(R.string.lbl_item))
        }
    }

    private fun addCount(count: Int, label: String) {
        if (count > 0) {
            addText("$count $label")
        }
    }

    private fun addSeriesAirs(item: BaseItemDto) {
        item.airDays?.takeIf { it.isNotEmpty() }?.let {
            addText(it[0].toString().plus(
                    if (item.airTime.isNotNullOrBlank())
                        " " + item.airTime
                    else ""
                )
            )
            addText("|")
        }
    }

    private fun addProgramChannel(item: BaseItemDto) {
        item.getProgramUnknownChannelName()?.let {
            addText(it)
        }
    }

    private fun addProgramInfo(item: BaseItemDto) {
        addText(item.getProgramSubText(context))
        if (item.isNew())
            addBlockTextRes(context.getString(R.string.lbl_new), R.drawable.dark_green_gradient, Color.GRAY)
        else if (item.isSeries == true && item.isNews == false)
            addBlockText(context.getString(R.string.lbl_repeat), context.getColor(R.color.lb_default_brand_color), Color.GRAY)

        if (item.isLive == true) {
            addBlockText(context.getString(R.string.lbl_live), context.getColor(R.color.lb_default_brand_color), Color.GRAY)
        }
    }

    private fun addSubText(rowItem: BaseRowItem) {
        addText(rowItem.getSubText(context))
    }

    private fun addRuntime(item: BaseItemDto) {
        item.runTimeTicks?.takeIf { it > 0 }?.let{ runTime ->
            val endTime = System.currentTimeMillis() + runTime.runtimeTicksToMs() - (item.userData?.playbackPositionTicks?.runtimeTicksToMs() ?: 0)
            val runTimeMin = runTime.runtimeTicksToMs().milliseconds.toMinutes()

            val text = nf.format(runTimeMin) +
                    context.getString(R.string.lbl_min) +
                    if (includeEndtime)
                        " (" + context.resources.getString(R.string.lbl_ends) + " " + DateFormat.getTimeFormat(context).format(Date(endTime)) + ")" else ""
            if (childCount > 0)
                addText("|")
            addText(text)
        }
    }

    private fun addSeasonEpisode(item: BaseItemDto) {
        item.indexNumber?.let { idx ->
            val textSeason = item.parentIndexNumber?.let { pIdx -> context.getString(R.string.lbl_season_number, pIdx) + ":" } ?: ""
            val textEpisode =
                if (item.indexNumberEnd.isNotNull()) context.getString(R.string.lbl_episode_range, idx, item.indexNumberEnd)
                else context.getString(R.string.lbl_episode_number, idx)

            addText("$textSeason$textEpisode")
        }
    }

    private fun addCriticInfo(item: BaseItemDto) {
        item.communityRating?.let {
            addImage(R.drawable.ic_star)
            addText(String.format(Locale.US, "%.1f", it))
        }
        item.criticRating?.let {
            addImage(if (it > 59) R.drawable.ic_rt_fresh else R.drawable.ic_rt_rotten)
            addText(nfp.format(it / 100))
        }
    }

    private fun addDate(item: BaseItemDto) {
        val datePattern = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "d MMM yyyy"))
        when (item.type) {
            BaseItemKind.PERSON -> {
                val sb = StringBuilder()
                if (item.premiereDate != null) {
                    sb.append(context.getString(R.string.lbl_born))
                    sb.append(item.premiereDate!!.format(datePattern))
                }
                if (item.endDate != null) {
                    sb.append(String.format("  |  %s", context.getString(R.string.lbl_died)))
                    sb.append(item.endDate!!.format(datePattern))
                    sb.append(" (")
                    sb.append(TimeUtils.numYears(TimeUtils.getDate(item.premiereDate), TimeUtils.getDate(item.endDate)))
                    sb.append(")")
                } else {
                    if (item.premiereDate != null) {
                        sb.append(" (")
                        sb.append(TimeUtils.numYears(TimeUtils.getDate(item.premiereDate), Calendar.getInstance()))
                        sb.append(")")
                    }
                }
                addText(sb.toString())
            }
            BaseItemKind.PROGRAM, BaseItemKind.TV_CHANNEL -> {
                item.takeIf { item.startDate.isNotNull() && item.endDate.isNotNull() }?.let {
                    addText(DateFormat.getTimeFormat(context).format(it.startDate) + "-" + DateFormat.getTimeFormat(context).format(it.endDate))
                }
            }
            BaseItemKind.SERIES -> {
                item.productionYear?.takeIf { it > 0 }?.let {
                    nf.isGroupingUsed = false
                    addText(nf.format(it))
                }
            }
            else -> when {
                item.premiereDate.isNotNull() -> addText(DateFormat.getMediumDateFormat(context).format(TimeUtils.getDate(item.premiereDate)))
                item.productionYear.isNotNull() -> if (item.productionYear!! > 0) {
                    nf.isGroupingUsed = false
                    addText(nf.format(item.productionYear))
                }
            }
        }
    }

    private fun addParentalRating(item: BaseItemDto) {
        item.officialRating?.takeIf { it.isNotBlank() && it != "0" }?.let {
            when (it) {
                "R", "NC-17", "TV-MA", "18" -> addOutlineText(it, outlineColor = context.getColor(R.color.parental_rating_red).saturationHSV(0.3f))
                "Not Rated" -> addOutlineText(it, outlineColor = context.getColor(R.color.parental_rating_purple).saturationHSV(0.3f))
                else -> addOutlineText(it, outlineColor = context.getColor(R.color.parental_rating_green).saturationHSV(0.3f))
            }
        }
    }

    private fun addSeriesStatus(item: BaseItemDto) {
        item.takeIf { it.type == BaseItemKind.SERIES && it.status.isNotNullOrBlank() }?.let {
            val continuing = it.status.equals(SeriesStatus.CONTINUING.serialName, ignoreCase = true)
            val status = if (continuing) context.getString(R.string.lbl__continuing) else context.getString(R.string.lbl_ended)
            addBlockText(status, if (continuing) colorStatusGreen else colorStatusRed, Color.LTGRAY)
        }
    }

    /**
     * Badges for the server's VideoRangeType (ported from jellyfin-androidtv 9dd71b7).
     * Returns null when the range type is missing or unknown so the caller can fall back.
     */
    private fun videoRangeBadges(rangeType: String?): List<String>? = when (rangeType?.lowercase()) {
        "sdr" -> listOf("SDR")
        "hdr10" -> listOf("HDR10")
        "hdr10plus" -> listOf("HDR10+")
        "hlg" -> listOf("HLG")
        "dovi", "doviinvalid" -> listOf("Dolby Vision")
        "doviwithhdr10" -> listOf("Dolby Vision", "HDR10")
        "doviwithhlg" -> listOf("Dolby Vision", "HLG")
        "doviwithsdr" -> listOf("Dolby Vision", "SDR")
        "doviwithel" -> listOf("Dolby Vision EL")
        "doviwithhdr10plus" -> listOf("Dolby Vision", "HDR10+")
        "doviwithelhdr10plus" -> listOf("Dolby Vision EL", "HDR10+")
        else -> null
    }

    private fun requestVideoRange(item: BaseItemDto, mediaSourceId: String) {
        synchronized(pendingVideoRangeFetches) {
            if (!pendingVideoRangeFetches.add(mediaSourceId)) return
        }
        val lifecycleOwner = findViewTreeLifecycleOwner()
        if (lifecycleOwner == null) {
            synchronized(pendingVideoRangeFetches) { pendingVideoRangeFetches.remove(mediaSourceId) }
            return
        }
        lifecycleOwner.lifecycleScope.launch {
            val fetched = withContext(Dispatchers.IO) {
                runCatching { api.userLibraryApi.getItem(itemId = item.id).content }.getOrNull()
            }
            fetched?.mediaSources?.forEach { source ->
                val id = source.id ?: return@forEach
                val rangeType = source.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }?.videoRangeType
                videoRangeCache.put(id, rangeType ?: "")
            }
            if (videoRangeCache.get(mediaSourceId) == null) videoRangeCache.put(mediaSourceId, "")
            synchronized(pendingVideoRangeFetches) { pendingVideoRangeFetches.remove(mediaSourceId) }

            // Redraw if this view still shows the same item
            val shownId = baseItem?.id?.toString() ?: rowItem?.itemId
            if (fetched != null && shownId != null && shownId.equals(item.id.toString(), ignoreCase = true)) updateView()
        }
    }

    private fun addMediaDetails(mediaSource: MediaSourceInfo, item: BaseItemDto) {
        mediaSource.mediaStreams?.firstOrNull {
            it.isNotNull() &&
            it.type == MediaStreamType.VIDEO &&
            it.height.isNotNull() &&
            it.width.isNotNull() &&
            ((it.height ?: 0) + (it.width ?: 0)) > 0
        }?.let { videoStream ->
            addText("|")

            val height = videoStream.height!!
            val width = videoStream.width!!
            if (height > 3000 && width > 7000)
                addBlockText("8K", colorMediaGolden)
            else if (height > 1600 && width > 5000)
                addBlockText("5K", colorMediaGolden)
            else if (height > 1300 && width > 3600)
                addBlockText("4K", colorMediaGolden)
            else if (height > 1200 && width > 2400)
                addBlockText("2K", colorMediaVideo)
            else if (height > 790 && width > 1800)
                addBlockText("1080p", colorMediaVideo)
            else if (height > 700 && width > 1000)
                addBlockText("720p", colorMediaVideo)
            else
                addBlockText(context.getString(R.string.lbl_sd), colorMediaVideo)

            // 3D format (set by Jellyfin from file name tags like "3D.MVC"/"3D.HSBS", NFO or probing)
            item.video3dFormat?.let {
                val label = when (it) {
                    Video3dFormat.MVC -> "3D MVC"
                    Video3dFormat.FULL_SIDE_BY_SIDE -> "3D FSBS"
                    Video3dFormat.HALF_SIDE_BY_SIDE -> "3D HSBS"
                    Video3dFormat.FULL_TOP_AND_BOTTOM -> "3D FTAB"
                    Video3dFormat.HALF_TOP_AND_BOTTOM -> "3D HTAB"
                }
                addBlockText(label, colorMediaGolden)
            }

            // Video range: hybrid formats (e.g. Dolby Vision with HDR10 fallback) get one badge per format
            val rangeType = videoStream.videoRangeType
                ?: mediaSource.id?.let { videoRangeCache.get(it) }?.takeIf { it.isNotEmpty() }
            if (rangeType == null && mediaSource.id != null && videoRangeCache.get(mediaSource.id!!) == null) {
                requestVideoRange(item, mediaSource.id!!)
            }
            val videoRanges = videoRangeBadges(rangeType)
            if (videoRanges != null) {
                videoRanges.forEach {
                    if (it == "SDR") addBlockText(it, colorMediaVideo)
                    else addBlockTextRes(it, R.drawable.block_text_bg_rainbow)
                }
            } else if (videoStream.displayTitle?.contains("hdr", true) == true) {
                // older servers / unknown range type: fall back to the display title
                addBlockTextRes("HDR", R.drawable.block_text_bg_rainbow)
            } else if (videoStream.displayTitle?.contains("sdr", true) == true) {
                addBlockText("SDR", colorMediaVideo)
            }
            videoStream.codec?.let {
                addBlockText(it.trim().uppercase(), colorMediaVideo)
            }
        }

        mediaSource.mediaStreams?.filter {
            it.isNotNull() &&
            it.type == MediaStreamType.AUDIO &&
            it.channelLayout.isNotNullOrBlank() &&
            it.codec.isNotNullOrBlank()
        }?.let { audioStreams ->
            val hasDolby = audioStreams.firstOrNull { it.codec?.contains("ac3", true) == true }.isNotNull()
            val hasDolbyPlus = audioStreams.firstOrNull { it.codec?.contains("eac3", true) == true }.isNotNull()
            val hasTrueHD = audioStreams.firstOrNull { it.codec?.contains("truehd", true) == true }.isNotNull()
            val hasAtmos = audioStreams.firstOrNull { it.displayTitle?.contains("atmos", true) == true }.isNotNull()

            val hasDts = audioStreams.firstOrNull { it.codec?.contains("dca", true) == true }.isNotNull()
            val hasDtsHD = audioStreams.firstOrNull { it.profile?.contains("dts-hd", true) == true }.isNotNull()
            val hasDtsX = audioStreams.firstOrNull { it.displayTitle?.contains("dts:x", true) == true }.isNotNull()

            addText("|")

            val codecNameA = when {
                hasTrueHD -> "TrueHD"
                hasDolbyPlus -> "DD+"
                hasDolby -> "Dolby"
                else -> null
            }
            val codecNameB = when {
                hasDtsX -> "DTS:X"
                hasDtsHD -> "DTS-HD"
                hasDts -> "DTS"
                else -> null
            }

            codecNameA.takeIf { it.isNotNullOrBlank() }?.let {
                addBlockText(it, colorMediaAudio)
            }
            if (hasAtmos)
                addBlockText("Atmos", colorMediaAudio)

            codecNameB.takeIf { it.isNotNullOrBlank() }?.let {
                if (codecNameA.isNotNullOrBlank())
                    addText("/")
                addBlockText(it, colorMediaAudio)
            }

            if (codecNameA.isNullOrBlank() && codecNameB.isNullOrBlank())
                audioStreams.maxByOrNull { it.channels ?: 0 }?.codec?.let {
                    addBlockText(it.uppercase(), colorMediaAudio)
                }

            audioStreams.maxByOrNull { it.channels ?: 0 }?.channelLayout?.uppercase()?.let {
                addBlockText(it, colorMediaAudio)
            }

            val maxBitDepth = audioStreams.maxByOrNull { it.bitDepth ?: 0 }?.bitDepth ?: 0
            val maxSampleRate = audioStreams.maxByOrNull { it.sampleRate ?: 0 }?.sampleRate ?: 0

            if (maxBitDepth > 16 && maxSampleRate > 44000)
                addBlockText("Hi-Res", colorMediaAudio)
        }

        mediaSource.mediaStreams?.firstOrNull {
            it.isNotNull() &&
            it.type == MediaStreamType.SUBTITLE
        }?.let {
            addText("|")
            addBlockText("CC")
        }
    }

    private fun addSpacer(sizeDP: Int = 4) {
        val mSpacer = Space(context).apply {
            layoutParams = ViewGroup.LayoutParams(sizeDP.dp(context), MATCH_PARENT)
        }
        addView(mSpacer)
    }

    private fun addBlockTextRes(text: String, @DrawableRes backgroundRes: Int, @ColorInt textColor: Int = Color.BLACK, alpha: Float? = null) {
        addText(text, textColor, alpha)?.apply {
            setBackgroundResource(backgroundRes)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, (layoutTextSize - blockText_sizeOffset).coerceAtLeast(7.sp(context)).toFloat())
            setPaddingHorizontal(2.dp(context))
        }
    }

    private fun addBlockText(text: String, @ColorInt backgroundColor: Int = colorGreyMid, @ColorInt textColor: Int = Color.BLACK, alpha: Float? = null) {
        addText(text, textColor, alpha)?.apply {
            background = PaintDrawable(backgroundColor).apply {
                setCornerRadius(2.dp(context).toFloat())
            }
            setTextSize(TypedValue.COMPLEX_UNIT_PX, (layoutTextSize - blockText_sizeOffset).coerceAtLeast(7.sp(context)).toFloat())
            setPaddingHorizontal(2.dp(context))
        }
    }

    private fun addOutlineText(text: String, @ColorInt textColor: Int? = null, alpha: Float? = null, maxWidth: Int? = null, @ColorInt outlineColor: Int? = null) {
        addText(text, textColor, alpha, maxWidth)?.apply {
            background = GradientDrawable().apply {
                setStroke(1.dp(context), outlineColor ?: context.getColor(R.color.white_dim))
            }
            setTextSize(TypedValue.COMPLEX_UNIT_PX, (layoutTextSize - outlineText_sizeOffset).coerceAtLeast(7.sp(context)).toFloat())

            setPaddingHorizontal(2.dp(context))
        }
    }

    private fun addText(text: String, @ColorInt textColor: Int? = null, alpha: Float? = null, maxWidth: Int? = null): AppCompatTextView? {
        if (text.isBlank())
            return null

        return AppCompatTextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, layoutTextSize.toFloat())
            setText(text)
            setTextColor(textColor ?: context.getColorFromAttribute(android.R.attr.textColorSecondary))
            alpha?.let { this.alpha = it }
            maxWidth?.let {
                this.maxWidth = it
                ellipsize = TextUtils.TruncateAt.END
            }

            maxLines = 1
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
        }.also {
            addView(it)
        }
    }

    private fun addImage(@DrawableRes imageRes: Int, @ColorInt tintColor: Int? = null, alpha: Float? = null, maxWidth: Int? = null) {
        AppCompatImageView(context).apply {
            layoutParams = MarginLayoutParams(WRAP_CONTENT, MATCH_PARENT)
            setImageResource(imageRes)
            tintColor?.let { setColorFilter(tintColor, PorterDuff.Mode.DST_OUT) }
            alpha?.let { this.alpha = it }
            maxWidth?.let { this.maxWidth = it }

            image_vMargin.takeIf { it > 0 }?.let { setMarginVertical(it) }

            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }.also {
            addView(it)
        }
    }
}