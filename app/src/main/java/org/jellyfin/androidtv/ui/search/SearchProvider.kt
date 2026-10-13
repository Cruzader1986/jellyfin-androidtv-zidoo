package org.jellyfin.androidtv.ui.search

import android.content.Context
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ObjectAdapter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.presentation.CustomListRowPresenter
import kotlin.time.Duration.Companion.milliseconds

class SearchProvider(
	private val context: Context,
	private val lifecycle: Lifecycle,
) : SearchSupportFragment.SearchResultProvider {
	companion object {
		private val SEARCH_DELAY = 600.milliseconds
	}

	private val rowsAdapter = ArrayObjectAdapter(CustomListRowPresenter())
	private var previousQuery: String? = null
	private val searchRunnable = SearchRunnable(context, rowsAdapter)
	private var searchJob: Job? = null

	init {
		showRecentSearches()
	}

	override fun getResultsAdapter(): ObjectAdapter = rowsAdapter

	/** Query of the results currently shown, remembered when one of them is opened. */
	fun rememberCurrentQuery() = RecentSearches.add(context, previousQuery)

	private fun showRecentSearches() {
		rowsAdapter.clear()
		val recent = RecentSearches.get(context)
		if (recent.isEmpty()) return
		val adapter = ArrayObjectAdapter(RecentSearchPresenter()).apply { recent.forEach { add(RecentSearchItem(it)) } }
		rowsAdapter.add(ListRow(HeaderItem(context.getString(R.string.lbl_recent_searches)), adapter))
	}

	override fun onQueryTextChange(query: String): Boolean = search(query, true)
	override fun onQueryTextSubmit(query: String): Boolean {
		RecentSearches.add(context, query)
		return search(query, false)
	}

	private fun search(query: String, delayed: Boolean): Boolean {
		if (query.isBlank()) {
			searchJob?.cancel()
			previousQuery = null
			showRecentSearches()
			return true
		}

		// Don't search the same thing twice
		if (query == previousQuery) return false
		previousQuery = query

		searchJob?.cancel()
		searchJob = lifecycle.coroutineScope.launch {
			if (delayed) delay(SEARCH_DELAY)
			searchRunnable.run(query)
		}

		return true
	}
}
