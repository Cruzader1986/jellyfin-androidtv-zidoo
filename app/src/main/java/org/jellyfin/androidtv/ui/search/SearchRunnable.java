package org.jellyfin.androidtv.ui.search;

import android.content.Context;

import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter;
import org.jellyfin.androidtv.ui.presentation.CardPresenter;
import org.jellyfin.apiclient.interaction.EmptyResponse;
import org.jellyfin.androidtv.data.querying.StdItemQuery;
import org.jellyfin.apiclient.model.querying.ItemFields;
import org.jellyfin.apiclient.model.querying.ItemQuery;
import org.jellyfin.apiclient.model.querying.PersonsQuery;

import java.util.ArrayList;

import timber.log.Timber;

public class SearchRunnable {
    private Context context;
    private ArrayObjectAdapter mRowsAdapter;
    private ArrayList<ItemRowAdapter> searchItemRows;
    private int searchesReceived;

    public SearchRunnable(Context context, ArrayObjectAdapter adapter) {
        this.context = context;
        this.mRowsAdapter = adapter;
        this.searchItemRows = new ArrayList<>();
    }

    public void run(String searchString) {
        mRowsAdapter.clear();
        searchItemRows.clear();
        searchesReceived = 0;

        //Get search results by type
        ItemQuery movies = getSearchQuery(new String[]{"Movie", "BoxSet"}, searchString);
        ItemRowAdapter movieAdapter = new ItemRowAdapter(context, movies, new CardPresenter(), mRowsAdapter);
        ListRow movieRow = new ListRow(new HeaderItem(context.getString(R.string.lbl_movies)), movieAdapter);
        movieAdapter.setRow(movieRow);
        retrieveSearchResult(movieAdapter);

        ItemQuery tvSeries = getSearchQuery(new String[]{"Series"}, searchString);
        ItemRowAdapter tvSeriesAdapter = new ItemRowAdapter(context, tvSeries, new CardPresenter(), mRowsAdapter);
        ListRow tvSeriesRow = new ListRow(new HeaderItem(context.getString(R.string.lbl_series)), tvSeriesAdapter);
        tvSeriesAdapter.setRow(tvSeriesRow);
        retrieveSearchResult(tvSeriesAdapter);

        ItemQuery tv = getSearchQuery(new String[]{"Episode"}, searchString);
        ItemRowAdapter tvAdapter = new ItemRowAdapter(context, tv, new CardPresenter(), mRowsAdapter);
        ListRow tvRow = new ListRow(new HeaderItem(context.getString(R.string.lbl_episodes)), tvAdapter);
        tvAdapter.setRow(tvRow);
        retrieveSearchResult(tvAdapter);

        PersonsQuery people = new PersonsQuery();
        people.setSearchTerm(searchString);
        people.setLimit(LIMIT);
        people.setRecursive(true);
        people.setFields(new ItemFields[]{ItemFields.PrimaryImageAspectRatio});
        ItemRowAdapter peopleAdapter = new ItemRowAdapter(context, people, new CardPresenter(), mRowsAdapter);
        ListRow peopleRow = new ListRow(new HeaderItem(context.getString(R.string.lbl_people)), peopleAdapter);
        peopleAdapter.setRow(peopleRow);
        retrieveSearchResult(peopleAdapter);

        ItemQuery videos = getSearchQuery(new String[]{"Video"}, searchString);
        ItemRowAdapter videoAdapter = new ItemRowAdapter(context, videos, new CardPresenter(), mRowsAdapter);
        ListRow videoRow = new ListRow(new HeaderItem(context.getString(R.string.lbl_videos)), videoAdapter);
        videoAdapter.setRow(videoRow);
        retrieveSearchResult(videoAdapter);

        ItemQuery recordings = getSearchQuery(new String[]{"Recording"}, searchString);
        ItemRowAdapter recordingAdapter = new ItemRowAdapter(context, recordings, new CardPresenter(), mRowsAdapter);
        ListRow recordingRow = new ListRow(new HeaderItem(context.getString(R.string.lbl_recordings)), recordingAdapter);
        recordingAdapter.setRow(recordingRow);
        retrieveSearchResult(recordingAdapter);

        ItemQuery programs = getSearchQuery(new String[]{"Program"}, searchString);
        ItemRowAdapter programAdapter = new ItemRowAdapter(context, programs, new CardPresenter(), mRowsAdapter);
        ListRow programRow = new ListRow(new HeaderItem(context.getString(R.string.lbl_programs)), programAdapter);
        programAdapter.setRow(programRow);
        retrieveSearchResult(programAdapter);

        ItemQuery artists = getSearchQuery(new String[]{"MusicArtist"}, searchString);
        ItemRowAdapter artistAdapter = new ItemRowAdapter(context, artists, new CardPresenter(), mRowsAdapter);
        ListRow artistRow = new ListRow(new HeaderItem(context.getString(R.string.lbl_artists)), artistAdapter);
        artistAdapter.setRow(artistRow);
        retrieveSearchResult(artistAdapter);

        ItemQuery albums = getSearchQuery(new String[]{"MusicAlbum"}, searchString);
        ItemRowAdapter albumAdapter = new ItemRowAdapter(context, albums, new CardPresenter(), mRowsAdapter);
        ListRow albumRow = new ListRow(new HeaderItem(context.getString(R.string.lbl_albums)), albumAdapter);
        albumAdapter.setRow(albumRow);
        retrieveSearchResult(albumAdapter);

        ItemQuery songs = getSearchQuery(new String[]{"Audio"}, searchString);
        ItemRowAdapter songAdapter = new ItemRowAdapter(context, songs, new CardPresenter(), mRowsAdapter);
        ListRow songRow = new ListRow(new HeaderItem(context.getString(R.string.lbl_songs)), songAdapter);
        songAdapter.setRow(songRow);
        retrieveSearchResult(songAdapter);
    }

    private void retrieveSearchResult(ItemRowAdapter itemRow) {
        searchItemRows.add(itemRow);

        itemRow.setRetrieveFinishedListener(new EmptyResponse() {
            @Override
            public void onResponse() {
                searchesReceived++;
                if (searchesReceived == searchItemRows.size()) {
                    for (ItemRowAdapter itemRowAdapter : searchItemRows) {
                        itemRowAdapter.addToParentIfResultsReceived();
                    }
                }
            }

            @Override
            public void onError(Exception ex) {
                // Log the error but call onResponse to make sure items will be displayed
                // even if one of the requests failed
                Timber.e(ex);
                onResponse();
            }
        });

        itemRow.Retrieve();
    }

    private static final int LIMIT = 50;

    /**
     * Search through the regular item lookup (/Items with a search term). The /Search/Hints answers of
     * current servers leave out fields the client libraries require, so they never load.
     */
    private static ItemQuery getSearchQuery(String[] itemTypes, String searchString) {
        StdItemQuery query = new StdItemQuery();
        query.setLimit(LIMIT);
        query.setSearchTerm(searchString);
        query.setIncludeItemTypes(itemTypes);
        query.setRecursive(true);
        query.setEnableTotalRecordCount(false);

        return query;
    }
}

