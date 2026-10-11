package org.jellyfin.androidtv.ui.browsing;

import android.os.Bundle;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.data.querying.StdItemQuery;
import org.jellyfin.androidtv.util.Utils;
import org.jellyfin.apiclient.model.dto.BaseItemType;
import org.jellyfin.apiclient.model.entities.SortOrder;
import org.jellyfin.apiclient.model.querying.ItemFilter;
import org.jellyfin.sdk.model.constant.ItemSortBy;

import java.util.Arrays;

public class GenericFolderFragment extends EnhancedBrowseFragment {
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    private static BaseItemType[] showSpecialViewTypes = new BaseItemType[]{BaseItemType.CollectionFolder, BaseItemType.Folder, BaseItemType.UserView, BaseItemType.ChannelFolderItem};

    @Override
    protected void setupQueries(RowLoader rowLoader) {
        if (Utils.getSafeValue(mFolder.getChildCount(), 0) > 0 ||
                mFolder.getBaseItemType() == BaseItemType.Channel ||
                mFolder.getBaseItemType() == BaseItemType.ChannelFolderItem ||
                mFolder.getBaseItemType() == BaseItemType.UserView ||
                mFolder.getBaseItemType() == BaseItemType.CollectionFolder) {
            boolean showSpecialViews = Arrays.asList(showSpecialViewTypes).contains(mFolder.getBaseItemType()) && !"channels".equals(mFolder.getCollectionType());

            if (showSpecialViews) {
                if (mFolder.getBaseItemType() != BaseItemType.ChannelFolderItem) {
                    StdItemQuery resume = new StdItemQuery();
                    resume.setParentId(mFolder.getId());
                    resume.setLimit(50);
                    resume.setFilters(new ItemFilter[]{ItemFilter.IsResumable});
                    resume.setSortBy(new String[]{ItemSortBy.DatePlayed});
                    resume.setSortOrder(SortOrder.Descending);
                    mRows.add(new BrowseRowDef(getString(R.string.lbl_continue_watching), resume));
                }

                StdItemQuery latest = new StdItemQuery();
                latest.setParentId(mFolder.getId());
                latest.setLimit(50);
                latest.setFilters(new ItemFilter[]{ItemFilter.IsUnplayed});
                latest.setSortBy(new String[]{ItemSortBy.DateCreated});
                latest.setSortOrder(SortOrder.Descending);
                mRows.add(new BrowseRowDef(getString(R.string.lbl_latest_additions), latest));

            }

            StdItemQuery byName = new StdItemQuery();
            byName.setParentId(mFolder.getId());
            String header = (mFolder.getBaseItemType() == BaseItemType.Season) ? mFolder.getName() : getString(R.string.lbl_by_name);
            mRows.add(new BrowseRowDef(header, byName).setChunkSize(100));

            // Season page: quick navigation to the other seasons and back to the series
            if (mFolder.getBaseItemType() == BaseItemType.Season) {
                String seriesId = mFolder.getSeriesId() != null ? mFolder.getSeriesId() : mFolder.getParentId();
                if (seriesId != null) {
                    StdItemQuery seasons = new StdItemQuery();
                    seasons.setParentId(seriesId);
                    seasons.setIncludeItemTypes(new String[]{"Season"});
                    seasons.setSortBy(new String[]{ItemSortBy.SortName});
                    mRows.add(new BrowseRowDef(getString(R.string.lbl_seasons), seasons).setChunkSize(100));

                    StdItemQuery series = new StdItemQuery();
                    series.setIds(new String[]{seriesId});
                    mRows.add(new BrowseRowDef(getString(R.string.lbl_series), series));
                }
            }

            rowLoader.loadRows(mRows);
        }
    }
}
