package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import org.jellyfin.androidtv.constant.CardInfoType
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.util.SmallListRow
import org.jellyfin.androidtv.util.SmallListRowAbs
import org.jellyfin.androidtv.util.ThumbListRow
import kotlin.reflect.KClass

class HomeFragmentBrowseRowDefRow(
	private val browseRowDef: BrowseRowDef,
	private val rowClass: KClass<out ListRow> = ListRow::class,
	private val cardInfoType: CardInfoType = CardInfoType.NO_INFO,
	private val cardImageType: ImageType? = null
) : HomeFragmentRow {
	override fun addToRowsAdapter(context: Context, presenter: CardPresenter, rowsAdapter: ArrayObjectAdapter) {

		var presenterX = presenter
		// Modern home (forced info type): portrait covers everywhere, also for Continue Watching / Next Up
		val modern = presenter.mForceInfoType
		val infoType = if (modern) presenter.mInfoType else cardInfoType
		val imageType = if (modern && cardImageType == ImageType.THUMB) ImageType.POSTER else cardImageType
		val rowType = if (modern && rowClass == ThumbListRow::class) ListRow::class else rowClass
		if (infoType != presenter.mInfoType || imageType != presenter.mImageType)
			presenterX = CardPresenter(presenter).apply {
				mInfoType = infoType
				mImageType = imageType
				// An episode's own image is a 16:9 still, show its series' (or season's) poster instead
				if (imageType == ImageType.POSTER && modern && !mPreferSeasonForEpisodes) mPreferSeriesForEpisodes = true
			}

		val rowAdapter = ItemRowAdapter.buildItemRowAdapter(context, browseRowDef, presenterX, rowsAdapter)
		rowAdapter.setReRetrieveTriggers(browseRowDef.changeTriggers)

		val header = HeaderItem(browseRowDef.headerText)
		val row = when (rowType) {
			ThumbListRow::class -> ThumbListRow(header, rowAdapter)
			SmallListRow::class -> SmallListRow(header, rowAdapter)
			SmallListRowAbs::class -> SmallListRowAbs(header, rowAdapter)
			else -> ListRow(header, rowAdapter)
		}
		rowAdapter.setRow(row)
		rowAdapter.Retrieve()
		rowsAdapter.add(row)
	}
}
