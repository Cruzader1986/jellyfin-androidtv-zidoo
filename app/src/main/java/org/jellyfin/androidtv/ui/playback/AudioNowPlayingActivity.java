package org.jellyfin.androidtv.ui.playback;

import static org.koin.java.KoinJavaComponent.inject;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.data.service.BackgroundService;
import org.jellyfin.androidtv.databinding.ActivityAudioNowPlayingBinding;
import org.jellyfin.androidtv.preference.UserPreferences;
import org.jellyfin.androidtv.ui.AsyncImageView;
import org.jellyfin.androidtv.ui.ClockUserView;
import org.jellyfin.androidtv.ui.itemdetail.FullDetailsActivity;
import org.jellyfin.androidtv.ui.itemdetail.ItemListActivity;
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem;
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter;
import org.jellyfin.androidtv.ui.shared.BaseActivity;
import org.jellyfin.androidtv.util.ImageUtils;
import org.jellyfin.androidtv.util.KeyProcessor;
import org.jellyfin.androidtv.util.LayoutHelper;
import org.jellyfin.androidtv.util.TimeUtils;
import org.jellyfin.androidtv.util.Utils;
import org.jellyfin.apiclient.model.dto.BaseItemDto;

import java.util.ArrayList;

import kotlin.Lazy;
import timber.log.Timber;

public class AudioNowPlayingActivity extends BaseActivity {
    ActivityAudioNowPlayingBinding binding;
    private TextView mGenreRow;
    private ImageButton mPlayPauseButton;
    private ImageButton mNextButton;
    private ImageButton mPrevButton;
    private ImageButton mRepeatButton;
    private ImageButton mShuffleButton;
    private ImageButton mAlbumButton;
    private ImageButton mArtistButton;
    private ImageButton mSaveButton;
    private ClockUserView mClock;
    private RelativeLayout mMainView;
    private ImageView mLogoImage;

    private RelativeLayout mSSArea;
    private TextView mSSTime;
    private TextView mSSAlbumSong;
    private TextView mSSQueueStatus;
    private TextView mSSUpNext;
    private String mDisplayDuration;

    private DisplayMetrics mMetrics;

    private TextView mArtistName;
    private TextView mSongTitle;
    private TextView mAlbumTitle;
    private TextView mCurrentNdx;
    private AsyncImageView mPoster;
    private ProgressBar mCurrentProgress;
    private TextView mCurrentPos;
    private TextView mRemainingTime;
    private int mCurrentDuration;
    private RowsSupportFragment mRowsFragment;
    private ArrayObjectAdapter mRowsAdapter;

    private AudioNowPlayingActivity mActivity;
    private final Handler mLoopHandler = new Handler();

    private BaseItemDto mBaseItem;
    private ListRow mQueueRow;

    private boolean queueRowHasFocus = false;

    private long lastUserInteraction;
    private boolean ssActive;

    private Lazy<BackgroundService> backgroundService = inject(BackgroundService.class);
    private Lazy<MediaManager> mediaManager = inject(MediaManager.class);
    private Lazy<UserPreferences> userPreferences = inject(UserPreferences.class);

    private PopupMenu popupMenu;

    private LyricsController mLyrics;
    private TextView mLyricsView;
    private TextView mSSLyricsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAudioNowPlayingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        lastUserInteraction = System.currentTimeMillis();

        mActivity = this;

        mClock = binding.clock;
        mPoster = binding.poster;
        mArtistName = binding.artistTitle;
        mGenreRow = binding.genreRow;
        mSongTitle = binding.song;
        mAlbumTitle = binding.album;
        mCurrentNdx = binding.track;
        mMainView = binding.mainScroller;
        mLogoImage = binding.artistLogo;

        mSSArea = binding.ssInfoArea;
        mSSTime = binding.ssTime;
        mSSAlbumSong = binding.ssAlbumSong;
        mSSQueueStatus = binding.ssQueueStatus;
        mSSUpNext = binding.ssUpNext;
        mLyricsView = binding.lyrics;
        mSSLyricsView = binding.ssLyrics;
        mLyrics = new LyricsController(androidx.lifecycle.LifecycleOwnerKt.getLifecycleScope(this));

        mPlayPauseButton = binding.playPauseBtn;
        mPlayPauseButton.setContentDescription(getString(R.string.lbl_pause));
        mPlayPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ssActive) {
                    stopScreenSaver();
                } else {
                    mediaManager.getValue().playPauseAudio();
                }
                lastUserInteraction = System.currentTimeMillis();
            }
        });

        mPrevButton = binding.prevBtn;
        mPrevButton.setContentDescription(getString(R.string.lbl_prev_item));
        mPrevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ssActive) {
                    stopScreenSaver();
                } else {
                    mediaManager.getValue().prevAudioItem();
                }
                lastUserInteraction = System.currentTimeMillis();
            }
        });

        mNextButton = binding.nextBtn;
        mNextButton.setContentDescription(getString(R.string.lbl_next_item));
        mNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ssActive) {
                    stopScreenSaver();
                } else {
                    mediaManager.getValue().nextAudioItem();
                }
                lastUserInteraction = System.currentTimeMillis();
            }
        });

        mRepeatButton = binding.repeatBtn;
        mRepeatButton.setContentDescription(getString(R.string.lbl_repeat));
        mRepeatButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ssActive) {
                    stopScreenSaver();
                } else {
                    mediaManager.getValue().toggleRepeat();
                    updateButtons(mediaManager.getValue().isPlayingAudio());
                }
                lastUserInteraction = System.currentTimeMillis();
            }
        });

        mSaveButton = findViewById(R.id.saveBtn);
        mSaveButton.setContentDescription(getString(R.string.lbl_save_as_playlist));
        mSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ssActive) {
                    stopScreenSaver();
                } else {
                    mediaManager.getValue().saveAudioQueue(mActivity);
                }
                lastUserInteraction = System.currentTimeMillis();
            }
        });

        mShuffleButton = binding.shuffleBtn;
        mShuffleButton.setContentDescription(getString(R.string.lbl_shuffle_queue));
        mShuffleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ssActive) {
                    stopScreenSaver();
                } else {
                    mediaManager.getValue().shuffleAudioQueue();
                    updateButtons(mediaManager.getValue().isPlayingAudio());
                }
                lastUserInteraction = System.currentTimeMillis();
            }
        });

        mAlbumButton = binding.albumBtn;
        mAlbumButton.setContentDescription(getString(R.string.lbl_open_album));
        mAlbumButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ssActive) {
                    stopScreenSaver();
                    lastUserInteraction = System.currentTimeMillis();
                } else {
                    Intent album = new Intent(mActivity, ItemListActivity.class);
                    album.putExtra("ItemId", mBaseItem.getAlbumId());
                    mActivity.startActivity(album);
                }
            }
        });

        mArtistButton = binding.artistBtn;
        mArtistButton.setContentDescription(getString(R.string.lbl_open_artist));
        mArtistButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ssActive) {
                    stopScreenSaver();
                    lastUserInteraction = System.currentTimeMillis();
                } else if (mBaseItem.getAlbumArtists() != null && mBaseItem.getAlbumArtists().size() > 0) {
                    Intent artist = new Intent(mActivity, FullDetailsActivity.class);
                    artist.putExtra("ItemId", mBaseItem.getAlbumArtists().get(0).getId());
                    mActivity.startActivity(artist);
                }
            }
        });

        mCurrentProgress = binding.playerProgress;
        mCurrentPos = binding.currentPos;
        mRemainingTime = binding.remainingTime;

        backgroundService.getValue().attach(this);
        mMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(mMetrics);

        mRowsFragment = new RowsSupportFragment();
        getSupportFragmentManager().beginTransaction().add(R.id.rowsFragment, mRowsFragment).commit();

        mRowsFragment.setOnItemViewClickedListener(new ItemViewClickedListener());
        ClassPresenterSelector rowPS = LayoutHelper.INSTANCE.buildDefaultRowPresenterSelector(null, 0);
        mRowsAdapter = new ArrayObjectAdapter(rowPS);
        mRowsFragment.setAdapter(mRowsAdapter);
        addQueue();

        mPlayPauseButton.requestFocus();
    }

    protected void addQueue() {
        mQueueRow = new ListRow(new HeaderItem("Current Queue"), mediaManager.getValue().getCurrentAudioQueue());
        mediaManager.getValue().getCurrentAudioQueue().setRow(mQueueRow);
        mRowsAdapter.add(mQueueRow);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadItem();
        lastUserInteraction = System.currentTimeMillis();
        //link events
        mediaManager.getValue().addAudioEventListener(audioEventListener);
        updateButtons(mediaManager.getValue().isPlayingAudio());

        // load the item duration and set the position to 0 since it won't be set elsewhere until playback is initialized
        if (!mediaManager.getValue().getIsAudioPlayerInitialized())
            setCurrentTime(0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        dismissPopup();
        mPoster.setKeepScreenOn(false);
        mediaManager.getValue().removeAudioEventListener(audioEventListener);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        lastUserInteraction = System.currentTimeMillis();

        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (mediaManager.getValue().isPlayingAudio()) mediaManager.getValue().pauseAudio();
                else mediaManager.getValue().resumeAudio();
                if (ssActive) {
                    stopScreenSaver();
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                mediaManager.getValue().nextAudioItem();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                mediaManager.getValue().prevAudioItem();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (ssActive) {
                    mediaManager.getValue().nextAudioItem();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (ssActive) {
                    mediaManager.getValue().prevAudioItem();
                    return true;
                }
        }

        if (ssActive) {
            stopScreenSaver();
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }

    private AudioEventListener audioEventListener = new AudioEventListener() {
        @Override
        public void onPlaybackStateChange(PlaybackController.PlaybackState newState, BaseItemDto currentItem) {
            Timber.d("**** Got playstate change: %s", newState.toString());
            if (newState == PlaybackController.PlaybackState.PLAYING && currentItem != mBaseItem) {
                // new item started
                loadItem();
                updateButtons(true);
            } else {
                updateButtons(newState == PlaybackController.PlaybackState.PLAYING);
                if (newState == PlaybackController.PlaybackState.IDLE && !mediaManager.getValue().hasNextAudioItem())
                    stopScreenSaver();
            }
        }

        @Override
        public void onProgress(long pos) {
            // start the screensaver after 60 seconds without user input
            // skip setting the time here if the screensaver will be started since startScreensaver() does it
            if (!ssActive && mediaManager.getValue().isPlayingAudio() && System.currentTimeMillis() - lastUserInteraction > 60000) {
                startScreenSaver();
            } else {
                setCurrentTime(pos);
                var item = mediaManager.getValue().getCurrentAudioItem();
                if (item != null) {
                    var presenters = mRowsAdapter.getPresenterSelector().getPresenters();
                    if (presenters != null && presenters[0] instanceof PositionableListRowPresenter) {
                        var presenter = (PositionableListRowPresenter) presenters[0];
                        if (mMainView.hasFocus() && presenter.getPosition() != mediaManager.getValue().getCurrentAudioQueuePosition()) {
                            presenter.setPositionSmooth(mediaManager.getValue().getCurrentAudioQueuePosition());
                        }
                    }
                }
            }
        }

        @Override
        public void onQueueStatusChanged(boolean hasQueue) {
            Timber.d("Queue status changed");
            if (hasQueue) {
                loadItem();
                if (mediaManager.getValue().getIsAudioPlayerInitialized()) {
                    updateButtons(mediaManager.getValue().isPlayingAudio());
                }
            } else {
                finish(); // entire queue removed nothing to do here
            }
        }

        @Override
        public void onQueueReplaced() {
            dismissPopup();
            mRowsAdapter.remove(mQueueRow);
            addQueue();
        }
    };

    private void updatePoster() {
        if (isFinishing()) return;
        // Figure image size
        Double aspect = ImageUtils.getImageAspectRatio(mBaseItem);
        int posterHeight = aspect > 1 ? Utils.convertDpToPixel(mActivity, 150) : Utils.convertDpToPixel(mActivity, 250);

        String primaryImageUrl = ImageUtils.getPrimaryImageUrl(mBaseItem, posterHeight);
        Timber.d("Audio Poster url: %s", primaryImageUrl);
        mPoster.load(primaryImageUrl, null, ContextCompat.getDrawable(this, R.drawable.ic_album), aspect, 0);
    }

    private void loadItem() {
        dismissPopup();
        mBaseItem = mediaManager.getValue().getCurrentAudioItem();
        if (mLyrics != null) mLyrics.load(mBaseItem != null ? mBaseItem.getId() : null);
        updateLyrics(-1);
        if (mBaseItem != null) {
            updatePoster();
            updateInfo(mBaseItem);
            mDisplayDuration = TimeUtils.formatMillis((mBaseItem.getRunTimeTicks() != null ? mBaseItem.getRunTimeTicks() : 0) / 10000);
            // give audio a chance to start playing before updating next info
            mLoopHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateSSInfo();
                }
            }, 750);
        }
    }

    private void updateButtons(final boolean playing) {
        Timber.d("Updating buttons");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPoster.setKeepScreenOn(playing);
                if (!playing) {
                    mPlayPauseButton.setImageResource(R.drawable.ic_play);
                    mPlayPauseButton.setContentDescription(getString(R.string.lbl_play));
                } else {
                    mPlayPauseButton.setImageResource(R.drawable.ic_pause);
                    mPlayPauseButton.setContentDescription(getString(R.string.lbl_pause));
                }
                mRepeatButton.setActivated(mediaManager.getValue().isRepeatMode());
                mSaveButton.setEnabled(mediaManager.getValue().getCurrentAudioQueueSize() > 1);
                mPrevButton.setEnabled(mediaManager.getValue().hasPrevAudioItem());
                mNextButton.setEnabled(mediaManager.getValue().hasNextAudioItem());
                mShuffleButton.setEnabled(mediaManager.getValue().getCurrentAudioQueueSize() > 1);
                mShuffleButton.setActivated(mediaManager.getValue().isShuffleMode());
                if (mBaseItem != null) {
                    mAlbumButton.setEnabled(mBaseItem.getAlbumId() != null);
                    mArtistButton.setEnabled(mBaseItem.getAlbumArtists() != null && mBaseItem.getAlbumArtists().size() > 0);
                }
            }
        });
    }

    private String getArtistName(BaseItemDto item) {
        String artistName = item.getArtists() != null && item.getArtists().size() > 0 ? item.getArtists().get(0) : item.getAlbumArtist();
        return artistName != null ? artistName : "";
    }

    private void updateInfo(BaseItemDto item) {
        if (item != null) {
            mArtistName.setText(getArtistName(item));
            mSongTitle.setText(item.getName());
            mAlbumTitle.setText(getResources().getString(R.string.lbl_now_playing_album, item.getAlbum()));
            mCurrentNdx.setText(getResources().getString(R.string.lbl_now_playing_track, mediaManager.getValue().getCurrentAudioQueueDisplayPosition(), mediaManager.getValue().getCurrentAudioQueueDisplaySize()));
            mCurrentDuration = ((Long) ((item.getRunTimeTicks() != null ? item.getRunTimeTicks() : 0) / 10000)).intValue();
            //set progress to match duration
            mCurrentProgress.setMax(mCurrentDuration);
            addGenres(mGenreRow);
            backgroundService.getValue().setBackground(item);
        }
    }

    private void updateLyrics(long time) {
        String line = mLyrics != null && time >= 0 ? mLyrics.lineAt(time) : null;
        if (mLyricsView != null) {
            mLyricsView.setVisibility(line != null ? View.VISIBLE : View.GONE);
            mLyricsView.setText(line != null ? line : "");
        }
        if (mSSLyricsView != null) {
            mSSLyricsView.setVisibility(line != null ? View.VISIBLE : View.GONE);
            mSSLyricsView.setText(line != null ? line : "");
        }
    }

    public void setCurrentTime(long time) {
        updateLyrics(time);
        if (ssActive) {
            mSSTime.setText(TimeUtils.formatMillis(time) + " / " + mDisplayDuration);
        } else {
            mCurrentProgress.setProgress(((Long) time).intValue());
            mCurrentPos.setText(TimeUtils.formatMillis(time));
            mRemainingTime.setText(mCurrentDuration > 0 ? "-" + TimeUtils.formatMillis(mCurrentDuration - time) : "");
        }
    }

    private void addGenres(TextView textView) {
        ArrayList<String> genres = mBaseItem.getGenres();
        textView.setText(genres == null ? "" : TextUtils.join(" / ", genres));
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(final Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (!(item instanceof BaseRowItem)) return;
            lastUserInteraction = System.currentTimeMillis();
            if (ssActive) {
                stopScreenSaver();
            } else {
                popupMenu = KeyProcessor.createItemMenu((BaseRowItem) item, ((BaseRowItem) item).getBaseItem().getUserData(), mActivity);
            }
        }
    }

    private void dismissPopup() {
        if (popupMenu != null) {
            popupMenu.dismiss();
            popupMenu = null;
        }
    }

    protected void startScreenSaver() {
        if (ssActive) return;
        dismissPopup();
        mArtistName.setAlpha(.3f);
        mGenreRow.setVisibility(View.INVISIBLE);
        mClock.setAlpha(.3f);
        mSSArea.setVisibility(View.VISIBLE);
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(mMainView, "alpha", 1f, 0f);
        fadeOut.setDuration(1000);
        fadeOut.start();
        fadeOut = ObjectAnimator.ofFloat(binding.rowsFragment, "alpha", 1f, 0f);
        fadeOut.setDuration(1000);
        fadeOut.start();

        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(mSSArea, "alpha", 0f, 1f);
        fadeIn.setDuration(1000);
        fadeIn.start();

        ssActive = true;
        setCurrentTime(mediaManager.getValue().getCurrentAudioPosition());
    }

    protected void stopScreenSaver() {
        if (!ssActive) return;
        if (mediaManager.getValue().hasAudioQueueItems()) {
            mPlayPauseButton.requestFocus();
        }
        mLogoImage.setVisibility(View.GONE);
        mArtistName.setAlpha(1f);
        mGenreRow.setVisibility(View.VISIBLE);
        mClock.setAlpha(1f);
        setCurrentTime(mediaManager.getValue().getCurrentAudioPosition());
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(mSSArea, "alpha", 1f, 0f);
        fadeOut.setDuration(1000);
        fadeOut.start();
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(mMainView, "alpha", 0f, 1f);
        fadeIn.setDuration(1000);
        fadeIn.start();
        fadeIn = ObjectAnimator.ofFloat(binding.rowsFragment, "alpha", 0f, 1f);
        fadeIn.setDuration(1000);
        fadeIn.start();
        mSSArea.setVisibility(View.GONE);
        lastUserInteraction = System.currentTimeMillis();
        ssActive = false;
    }

    protected void updateSSInfo() {
        mSSAlbumSong.setText((mBaseItem.getAlbum() != null ? mBaseItem.getAlbum() + " / " : "") + mBaseItem.getName());
        mSSQueueStatus.setText(mediaManager.getValue().getCurrentAudioQueueDisplayPosition() + " | " + mediaManager.getValue().getCurrentAudioQueueDisplaySize());
        BaseItemDto next = mediaManager.getValue().getNextAudioItem();
        mSSUpNext.setText(next != null ? getString(R.string.lbl_up_next_colon) + "  " + (getArtistName(next) != null ? getArtistName(next) + " / " : "") + next.getName() : "");
    }
}
