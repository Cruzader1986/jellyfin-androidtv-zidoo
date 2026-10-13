package org.jellyfin.androidtv.ui.home

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.CardInfoType
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences.Companion.cardInfoType
import org.jellyfin.androidtv.preference.UserSettingPreferences.Companion.homeSize
import org.jellyfin.androidtv.preference.UserSettingPreferences.Companion.seriesThumbnailsEnabled
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.browsing.MainActivity
import org.jellyfin.androidtv.ui.browsing.RowLoader
import org.jellyfin.androidtv.ui.browsing.StdRowsFragment
import org.jellyfin.androidtv.ui.playback.AudioEventListener
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.util.LayoutHelper
import org.jellyfin.androidtv.util.apiclient.callApi
import org.jellyfin.androidtv.util.setMarginTop
import org.jellyfin.apiclient.interaction.ApiClient
import org.jellyfin.apiclient.model.livetv.RecommendedProgramQuery
import org.jellyfin.apiclient.model.querying.ItemsResult
import org.koin.android.ext.android.inject
import timber.log.Timber

class HomeFragment : StdRowsFragment(), AudioEventListener {
	private val apiClient by inject<ApiClient>()
	private val mediaManager by inject<MediaManager>()
	private val userSettingPreferences by inject<UserSettingPreferences>()
	private val userPreferences by inject<UserPreferences>()
	private val userRepository by inject<UserRepository>()
	private val notificationsRepository by inject<NotificationsRepository>()
	private val helper by lazy { HomeFragmentHelper(requireContext(), userRepository, userSettingPreferences) }

	// Data
	private val rows = mutableListOf<HomeFragmentRow>()
	private var views: ItemsResult? = null
	private var includeLiveTvRows: Boolean = false

	// Special rows
	private val notificationsRow by lazy { NotificationsHomeFragmentRow(lifecycleScope, notificationsRepository) }
	private val nowPlaying by lazy { HomeFragmentNowPlayingRow(mediaManager) }
	private val liveTVRow by lazy { HomeFragmentLiveTVRow(requireActivity(), userRepository) }
	private val featuredRow by lazy {
		FeaturedHomeFragmentRow(lifecycleScope) {
			// The banner arrives after the other rows, focus it when the user hasn't moved yet
			if (selectedPosition <= 1) setSelectedPosition(0, false)
		}
	}

	private var homeSettingsUiHash: Int? = null
	private val modernLayout get() = userPreferences[UserPreferences.modernHomeLayout]

	/** Side menu "Home": back to the first row. */
	fun scrollToTop() {
		setSelectedPosition(0, true)
		view?.requestFocus()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		// get relevant settings hash for resume handling
		homeSettingsUiHash = userSettingPreferences.getUiSettingsHash() + userPreferences.getUiSettingsHash()

		// Create adapter/presenter and set it to parent
		mRowsAdapter = ArrayObjectAdapter(LayoutHelper.buildDefaultRowPresenterSelector(
			userSettingPreferences[homeSize].value,
			resources.getDimensionPixelSize(R.dimen.safe_area_vertical))
		)
		(mRowsAdapter.presenterSelector as? ClassPresenterSelector)?.addClassPresenter(FeaturedRow::class.java, FeaturedRowPresenter())
		adapter = mRowsAdapter

		val modern = userPreferences[UserPreferences.modernHomeLayout]
		mCardPresenter = CardPresenter(if (modern) CardInfoType.NO_INFO else userSettingPreferences[cardInfoType], null)
			.setAllowBackdropFallback(true)
			.setAllowParentFallback(userSettingPreferences[seriesThumbnailsEnabled])
			.setPreferSeasonForEpisodes(userSettingPreferences[seriesThumbnailsEnabled])
			.setPreferSeriesForEpisodes(userSettingPreferences[seriesThumbnailsEnabled])
		mCardPresenter.mForceInfoType = modern

		super.onCreate(savedInstanceState)

		// Subscribe to Audio messages
		mediaManager.addAudioEventListener(this)
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return super.onCreateView(inflater, container, savedInstanceState)?.apply {
			// Make sure to focus the cards instead of the toolbar
			ViewCompat.setFocusedByDefault(this, true)

			(this as? VerticalGridView)?.apply {
//				itemAlignmentOffsetPercent = WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
//				windowAlignmentOffsetPercent = WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
//				windowAlignmentOffsetPercent = 100f / userSettingPreferences[numHomeRowsScreen]
//				itemAlignmentOffsetPercent = 0f
				itemAlignmentOffset = -LayoutHelper.getToolbarHeight().div(2) + 1

				if (userPreferences[UserPreferences.modernHomeLayout]) {
					// Rows start under the preview of the focused title
					setMarginTop(HomePreviewView.height(requireContext()))
					itemAlignmentOffset = 0
					// Leave room for the side menu
					(layoutParams as? ViewGroup.MarginLayoutParams)?.let {
						it.leftMargin = HomeSideRail.collapsedWidth(requireActivity())
						layoutParams = it
					}
				} else {
					setMarginTop(resources.getDimensionPixelSize(R.dimen.safe_area_vertical))
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()

		if (homeSettingsUiHash != (userSettingPreferences.getUiSettingsHash() + userPreferences.getUiSettingsHash())) {
			mediaManager.setManagedAudioQueuePresenter(null)
			mediaManager.createManagedAudioQueue(true)
			val intent = Intent(context, MainActivity::class.java)
			// Clear navigation history
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
			activity?.finish()
			activity?.startActivity(intent)
		}
		// Update audio queue
		Timber.i("Updating audio queue in HomeFragment (onResume)")
		nowPlaying.update(requireContext(), mRowsAdapter)
	}

	override fun onQueueStatusChanged(hasQueue: Boolean) {
		if (activity == null || requireActivity().isFinishing) return

		Timber.i("Updating audio queue in HomeFragment (onQueueStatusChanged)")
		nowPlaying.update(requireContext(), mRowsAdapter)
	}

	override fun onDestroy() {
		super.onDestroy()

		mediaManager.setManagedAudioQueuePresenter(null)
		mediaManager.createManagedAudioQueue(true)
		mediaManager.removeAudioEventListener(this)
	}

	override fun setupEventListeners() {
		super.setupEventListeners()

		mSelectedListener.registerListener(object : OnItemViewSelectedListener {
			override fun onItemSelected(itemViewHolder: Presenter.ViewHolder?, item: Any?, rowViewHolder: RowPresenter.ViewHolder?, row: Row?) {
				val preview = (activity as? MainActivity)?.homePreview ?: return
				val baseItem = (item as? BaseRowItem)?.baseItem
				if (baseItem?.id != null) preview.show(baseItem.id, baseItem.name)
				else preview.clear()
			}
		})
		mClickedListener.registerListener(liveTVRow::onItemClicked)
		mClickedListener.registerListener(notificationsRow::onItemClicked)
	}

	override fun setupQueries(rowLoader: RowLoader) {
		val currentUser = userRepository.currentUser.value
		if (currentUser == null) {
			activity?.finish()
			return
		}

		lifecycleScope.launch(Dispatchers.IO) {
			// Start out with default sections
			val homesections = userSettingPreferences.homesections

			// Load the live TV check and the user views at the same time instead of one after another
			val liveTv = async {
				if (homesections.contains(HomeSectionType.LIVE_TV) && currentUser.policy?.enableLiveTvAccess == true) {
					// This is kind of ugly, but it mirrors how web handles the live TV rows on the home screen
					// If we can retrieve one live TV recommendation, then we should display the rows
					callApi<ItemsResult> {
						apiClient.GetRecommendedLiveTvProgramsAsync(
							RecommendedProgramQuery().apply {
								userId = currentUser.id.toString()
								enableTotalRecordCount = false
								imageTypeLimit = 1
								isAiring = true
								limit = 1
							},
							it
						)
					}.let { !it.items.isNullOrEmpty() }
				} else false
			}
			val userViews = async {
				if (homesections.contains(HomeSectionType.LATEST_MEDIA)) {
					callApi<ItemsResult> { apiClient.GetUserViews(currentUser.id.toString(), it) }
				} else null
			}
			includeLiveTvRows = liveTv.await()
			userViews.await()?.let { views = it }

			// Make sure the rows are empty
			rows.clear()

			// Check for coroutine cancellation
			if (!isActive) return@launch

			// Actually add the sections
			homesections.forEach(::addSection)

			// Add sections to layout
			withContext(Dispatchers.Main) {
				// Add rows in order
				notificationsRow.addToRowsAdapter(requireContext(), CardPresenter(CardInfoType.UNDER_MINI, null), mRowsAdapter)
				nowPlaying.addToRowsAdapter(requireContext(), CardPresenter(userSettingPreferences[cardInfoType], null), mRowsAdapter)
				if (userPreferences[UserPreferences.featuredBannerEnabled] && !userPreferences[UserPreferences.modernHomeLayout]) featuredRow.addToRowsAdapter(requireContext(), mCardPresenter, mRowsAdapter)
				rows.forEach {
					it.addToRowsAdapter(requireContext(), mCardPresenter, mRowsAdapter)
				}
				// Manually set focus if focusedByDefault is not available
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) view?.requestFocus()
			}
		}
	}

	private fun addSection(type: HomeSectionType) {
		when (type) {
			HomeSectionType.LATEST_MEDIA -> rows.add(helper.loadRecentlyAdded(views!!))
			// The modern layout lists the libraries in the side menu instead
			HomeSectionType.LIBRARY_TILES_SMALL -> if (!modernLayout) rows.add(helper.loadLibraryTiles())
			HomeSectionType.LIBRARY_BUTTONS -> if (!modernLayout) rows.add(helper.loadLibraryTiles(true))
			HomeSectionType.RESUME -> rows.add(helper.loadResumeVideo())
			HomeSectionType.RESUME_AUDIO -> rows.add(helper.loadResumeAudio())
			HomeSectionType.RESUME_BOOK -> Unit // Books are not (yet) supported
			HomeSectionType.ACTIVE_RECORDINGS -> rows.add(helper.loadLatestLiveTvRecordings())
			HomeSectionType.NEXT_UP -> rows.add(helper.loadNextUp())
			HomeSectionType.LIVE_TV -> if (includeLiveTvRows) {
				rows.add(liveTVRow)
				rows.add(helper.loadOnNow())
			}
			HomeSectionType.NONE -> Unit
		}
	}

	override fun loadRows(rows: List<BrowseRowDef>) {
		// Override to make sure it is ignored because we manage our own rows
	}
}
