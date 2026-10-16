package org.jellyfin.androidtv.ui.presentation;

import timber.log.Timber;

public class PositionableListRowPresenter extends CustomListRowPresenter {
    public PositionableListRowPresenter() {
        super();
    }

    public PositionableListRowPresenter(int rowHeightPx, int headerHeightPx) {
        super(rowHeightPx, headerHeightPx);
    }

    public PositionableListRowPresenter(int rowHeightPx, int headerHeightPx, int headerGapPx, int minCardSpacingPx) {
        super(rowHeightPx, headerHeightPx, headerGapPx, minCardSpacingPx);
    }

    public void setPosition(int ndx) {
        Timber.d("Setting position to: %d", ndx);

        if (viewHolder != null && viewHolder.getGridView() != null)
            viewHolder.getGridView().setSelectedPosition(ndx);
    }

    public void setPositionSmooth(int ndx) {
        Timber.d("Setting position to: %d", ndx);

        if (viewHolder != null && viewHolder.getGridView() != null)
            viewHolder.getGridView().setSelectedPositionSmooth(ndx);
    }

    public int getPosition() {
        return viewHolder != null && viewHolder.getGridView() != null ? viewHolder.getGridView().getSelectedPosition() : -1;
    }
}
