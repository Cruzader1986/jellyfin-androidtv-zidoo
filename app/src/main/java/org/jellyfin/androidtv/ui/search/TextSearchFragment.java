package org.jellyfin.androidtv.ui.search;

import static org.koin.java.KoinJavaComponent.inject;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.ListRow;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.data.service.BackgroundService;
import org.jellyfin.androidtv.databinding.FragmentSearchTextBinding;
import org.jellyfin.androidtv.ui.browsing.CompositeSelectedListener;
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem;
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher;
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter;

import kotlin.Lazy;

public class TextSearchFragment extends Fragment implements TextWatcher, TextView.OnEditorActionListener {
    protected CompositeSelectedListener mSelectedListener = new CompositeSelectedListener();

    private SearchProvider searchProvider;

    private Lazy<BackgroundService> backgroundService = inject(BackgroundService.class);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        searchProvider = new SearchProvider(getContext(), getLifecycle());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return FragmentSearchTextBinding.inflate(inflater, container, false).getRoot();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        backgroundService.getValue().attach(requireActivity());

        // Add event listeners
        EditText searchBar = getActivity().findViewById(R.id.search_bar);
        searchBar.addTextChangedListener(this);
        searchBar.setOnEditorActionListener(this);

        // Set up result fragment
        RowsSupportFragment rowsSupportFragment = (RowsSupportFragment) getChildFragmentManager().findFragmentById(R.id.results_frame);
        rowsSupportFragment.setAdapter(searchProvider.getResultsAdapter());

        rowsSupportFragment.setOnItemViewSelectedListener(mSelectedListener);
        mSelectedListener.registerListener((itemViewHolder, item, rowViewHolder, row) -> {
            if (!(item instanceof BaseRowItem)) {
                backgroundService.getValue().clearBackgrounds();
            } else {
                BaseRowItem rowItem = (BaseRowItem) item;
                // Results are regular items now (not search hints)
                if (rowItem.getSearchHint() != null) backgroundService.getValue().setBackground(rowItem.getSearchHint());
                else backgroundService.getValue().setBackground(rowItem.getBaseItem());
            }
        });

        // Create click listener
        rowsSupportFragment.setOnItemViewClickedListener((itemViewHolder, item, rowViewHolder, row) -> {
            if (item instanceof RecentSearchItem) {
                String query = ((RecentSearchItem) item).getQuery();
                searchBar.setText(query);
                searchBar.setSelection(query.length());
                searchProvider.onQueryTextSubmit(query);
                return;
            }
            if (!(item instanceof BaseRowItem)) return;
            searchProvider.rememberCurrentQuery();

            ItemLauncher.launch((BaseRowItem) item, (ItemRowAdapter) ((ListRow) row).getAdapter(), ((BaseRowItem) item).getIndex(), getActivity());
        });
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // No implementation
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // No implementation
    }

    @Override
    public void afterTextChanged(Editable s) {
        searchProvider.onQueryTextChange(s.toString());
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        // Detect keyboard "submit" actions
        if (actionId == EditorInfo.IME_ACTION_SEARCH)
            searchProvider.onQueryTextSubmit(v.getText().toString());

        // Return "false" to automatically close keyboard
        return false;
    }
}

