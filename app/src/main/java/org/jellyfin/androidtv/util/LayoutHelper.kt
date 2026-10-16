package org.jellyfin.androidtv.util

import androidx.annotation.IntRange
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.ListRow
import me.carleslc.kotlinextensions.number.roundDiv
import me.carleslc.kotlinextensions.number.roundToInt
import org.jellyfin.androidtv.JellyfinApplication
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.CardInfoType
import org.jellyfin.androidtv.constant.FocusZoomSize
import org.jellyfin.androidtv.constant.GridDirection
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter

object LayoutHelper {
    private const val SMALL_ROW_RATIO = 2 // half poster height
    private const val THUMB_ROW_RATIO = 1.0 / 0.75 // 1.333..

    fun getScreenAspectRatio(): Double = JellyfinApplication.appContext.resources.let { res ->
       res.displayMetrics.heightPixels.toDouble() / res.displayMetrics.widthPixels.toDouble()
    }

    fun getDefaultRowHeights(): Pair<Int, Int> {
        return calcRowHeights(null,0,false)
    }

    // return rowHeight, headerHeight
    fun calcRowHeights(
        @IntRange(from = 1, to = 10) numScreenRows: Int?,
        extraMarginPx: Int = 0,
        adaptiveHeaderHeight: Boolean = false
    ): Pair<Int, Int> {
        return JellyfinApplication.appContext.resources.let { res ->
            val numRows = numScreenRows ?: res.getInteger(R.integer.default_card_rows_per_screen)
            val rowContainerHeightPx = res.displayMetrics.heightPixels.minus(extraMarginPx).floorDiv(numRows)
            val rowHeaderHeightPx = when(adaptiveHeaderHeight) {
                true -> {
                    val headerPct = res.getFraction(R.fraction.row_header_height_pct, 1, 1)
                    rowContainerHeightPx - (rowContainerHeightPx / (1 + headerPct)).roundDiv(2).times(2).toInt()
                }
                false -> getDefaultRowHeaderHeight()
            }
            Pair(rowContainerHeightPx - rowHeaderHeightPx, rowHeaderHeightPx)
        }
    }

    fun getToolbarHeight(): Int {
        return JellyfinApplication.appContext.resources.run {
            getFraction(R.fraction.toolbar_layout_height_pct, 1, 1).let {
                (displayMetrics.heightPixels * it).roundDiv(2).times(2).toInt()
            }
        }
    }

    fun getDefaultRowHeaderHeight(): Int {
        return JellyfinApplication.appContext.resources.run {
            getFraction(R.fraction.row_header_height_pct_abs, 1, 1).let {
                (displayMetrics.heightPixels * it).roundDiv(2).times(2).toInt()
            }
        }
    }

    fun buildDefaultRowPresenterSelector(numScreenRows: Int?, extraMarginPx: Int = 0): ClassPresenterSelector {
        return JellyfinApplication.appContext.resources.let {res ->
            val numRows = numScreenRows ?: res.getInteger(R.integer.default_card_rows_per_screen)
            val rowContainerHeight = res.displayMetrics.heightPixels.minus(extraMarginPx).floorDiv(numRows)
            val rowHeaderHeightPx = getDefaultRowHeaderHeight()

            val rowPresenter = PositionableListRowPresenter(rowContainerHeight - rowHeaderHeightPx, rowHeaderHeightPx)
            val thumbRowPresenter = PositionableListRowPresenter(rowContainerHeight.div(THUMB_ROW_RATIO).toInt() - rowHeaderHeightPx, rowHeaderHeightPx)
            val smallRowPresenter = PositionableListRowPresenter(rowContainerHeight.floorDiv(SMALL_ROW_RATIO) - rowHeaderHeightPx, rowHeaderHeightPx)

            val absRowContainerHeightPx = res.displayMetrics.heightPixels.minus(extraMarginPx).times(res.getFraction(R.fraction.button_row_layout_height_pct, 1, 1)).roundToInt()
            val smallAbsRowPresenter = PositionableListRowPresenter(absRowContainerHeightPx - rowHeaderHeightPx, rowHeaderHeightPx)

            val cPS = ClassPresenterSelector()
            cPS.addClassPresenter(ListRow::class.java, rowPresenter)
            cPS.addClassPresenter(ThumbListRow::class.java, thumbRowPresenter)
            cPS.addClassPresenter(SmallListRow::class.java, smallRowPresenter)
            cPS.addClassPresenter(SmallListRowAbs::class.java, smallAbsRowPresenter)
            return@let cPS
        }
    }

    /**
     * Rows for the modern home: they live in [availableHeightPx] under the preview, so size them
     * for that space (three poster rows with gaps between them) instead of the whole screen,
     * and leave room between the row titles and the cards.
     */
    fun buildModernHomeRowPresenterSelector(availableHeightPx: Int, rowGapPx: Int): ClassPresenterSelector {
        return JellyfinApplication.appContext.resources.let { res ->
            val density = res.displayMetrics.density
            val rows = 3
            // The focused card grows by up to ~18% (focus zoom), about 8dp past its top and bottom edge,
            // so the gap under the row title has to be wider than that or the zoomed card covers the title
            val headerHeightPx = (30 * density).toInt()
            val headerGapPx = (12 * density).toInt()
            val cardSpacingPx = (12 * density).toInt()
            val posterHeightPx = (availableHeightPx - (8 * density).toInt() - (rows - 1) * rowGapPx) / rows - headerHeightPx
            // Wide cards (live TV and the like) keep the same height so every row lines up
            val thumbHeightPx = posterHeightPx
            val smallHeightPx = (posterHeightPx * 0.6).toInt()

            fun presenter(height: Int) = PositionableListRowPresenter(height, headerHeightPx, headerGapPx, cardSpacingPx)
            ClassPresenterSelector().apply {
                addClassPresenter(ListRow::class.java, presenter(posterHeightPx))
                addClassPresenter(ThumbListRow::class.java, presenter(thumbHeightPx))
                addClassPresenter(SmallListRow::class.java, presenter(smallHeightPx))
                addClassPresenter(SmallListRowAbs::class.java, presenter(smallHeightPx))
            }
        }
    }

    fun estimateCardsOnScreen(numColRows: Int, gridDirection: GridDirection, aspect: Double, cardSpacingPx: Int, extraVMarginPx: Int = 0): Int {
        return JellyfinApplication.appContext.resources.let { res ->
            when (gridDirection) {
                GridDirection.HORIZONTAL -> {
                    val spacingVTotal = (numColRows - 1) * cardSpacingPx
                    val spacingHTotal = spacingVTotal * getScreenAspectRatio()
                    val stride = res.displayMetrics.widthPixels.minus(spacingHTotal).div(res.displayMetrics.heightPixels.minus(extraVMarginPx + spacingVTotal).div(numColRows) * aspect)
                    return@let (numColRows * stride).toInt()
                }
                GridDirection.VERTICAL -> {
                    val spacingHTotal = (numColRows - 1) * cardSpacingPx
                    val spacingVTotal = spacingHTotal / getScreenAspectRatio()
                    val stride = res.displayMetrics.heightPixels.minus(extraVMarginPx + spacingVTotal).div(res.displayMetrics.widthPixels.minus(spacingHTotal).div(numColRows) / aspect)
                    return@let (numColRows * stride).toInt()
                }
            }
        }
    }

    fun getStatusBarLayoutHeight(): Int {
        return JellyfinApplication.appContext.resources.let { res ->
            val height1 = res.getFraction(R.fraction.info_row_layout_height_pct, 1, 1).let {
                (res.displayMetrics.heightPixels * it).toInt()
            }
            val height2 = res.getFraction(R.fraction.title_layout_height_pct, 1, 1).let {
                (res.displayMetrics.heightPixels * it).toInt()
            }
            val heightPadding = res.getDimensionPixelSize(R.dimen.safe_area_vertical) + res.getDimensionPixelSize(R.dimen.safe_area_vertical_small)
            return@let (height1 + height2 + heightPadding)
        }
    }

    fun calcGridPadding(zoomSize: FocusZoomSize, infoType: CardInfoType, numColRows: Int, gridDirection: GridDirection, aspect: Double, cardSpacingPx: Int, extraVMarginPx: Int = 0): Pair<Int, Int> {
        return JellyfinApplication.appContext.resources.let { res ->
            val zoomVScale = when(infoType) {
                CardInfoType.UNDER_MINI -> (zoomSize.getPctValue() + 0.025f)
                CardInfoType.UNDER_FULL -> (zoomSize.getPctValue() + 0.045f)
                else -> zoomSize.getPctValue()
            }
            when (gridDirection) {
                GridDirection.HORIZONTAL -> {
                    val spacingVTotal = (numColRows - 1) * cardSpacingPx
                    val cardHeight = res.displayMetrics.heightPixels.minus(extraVMarginPx + spacingVTotal).div(numColRows)
                    val paddingV = (cardHeight * zoomVScale) - cardHeight

                    val cardWidth = cardHeight * aspect
                    val paddingH = (cardWidth * zoomSize.getPctValue()) - cardWidth
                    return@let Pair(paddingV.div(2).toInt().coerceAtLeast(0), paddingH.div(2).toInt().coerceAtLeast(0))
                }

                GridDirection.VERTICAL -> {
                    val spacingHTotal = (numColRows - 1) * cardSpacingPx
                    val cardWidth = res.displayMetrics.widthPixels.minus(spacingHTotal).div(numColRows)
                    val paddingH = (cardWidth * zoomSize.getPctValue()) - cardWidth
                    val cardHeight = cardWidth / aspect
                    val paddingV = (cardHeight * zoomVScale) - cardHeight

                    return@let Pair(paddingV.div(2).toInt().coerceAtLeast(0), paddingH.div(2).toInt().coerceAtLeast(0))
                }
            }
        }
    }
}