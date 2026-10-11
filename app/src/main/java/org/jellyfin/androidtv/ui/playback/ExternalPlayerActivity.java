package org.jellyfin.androidtv.ui.playback;

import static org.jellyfin.androidtv.util.AudioSubtitleHelper.getAbsoluteAudioSubIdxSafe;
import static org.jellyfin.androidtv.util.AudioSubtitleHelper.getBestAudioSubtitleIdx;
import static org.jellyfin.androidtv.util.Utils.RUNTIME_TICKS_TO_MS;
import static org.jellyfin.androidtv.util.Utils.getMillisecondsFormated;
import static org.jellyfin.androidtv.util.Utils.isEmpty;
import static org.jellyfin.androidtv.util.Utils.isEmptyTrim;
import static org.jellyfin.androidtv.util.Utils.isNonEmpty;
import static org.jellyfin.androidtv.util.Utils.isNonEmptyTrim;
import static org.koin.java.KoinJavaComponent.inject;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.FragmentActivity;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.auth.repository.UserRepository;
import org.jellyfin.androidtv.data.compat.PlaybackException;
import org.jellyfin.androidtv.data.compat.StreamInfo;
import org.jellyfin.androidtv.data.compat.SubtitleStreamInfo;
import org.jellyfin.androidtv.data.compat.VideoOptions;
import org.jellyfin.androidtv.data.service.BackgroundService;
import org.jellyfin.androidtv.preference.LibraryPreferences;
import org.jellyfin.androidtv.preference.PreferencesRepository;
import org.jellyfin.androidtv.preference.UserPreferences;
import org.jellyfin.androidtv.preference.constant.AudioCodecOut;
import org.jellyfin.androidtv.preference.constant.LanguagesAudio;
import org.jellyfin.androidtv.preference.constant.NextUpBehavior;
import org.jellyfin.androidtv.preference.constant.PreferredVideoPlayer;
import org.jellyfin.androidtv.ui.playback.nextup.NextUpActivity;
import org.jellyfin.androidtv.util.AudioSubtitleHelper;
import org.jellyfin.androidtv.util.DeviceUtils;
import org.jellyfin.androidtv.util.ImageHelper;
import org.jellyfin.androidtv.util.Utils;
import org.jellyfin.androidtv.util.apiclient.BaseItemUtils;
import org.jellyfin.androidtv.util.apiclient.ReportingHelper;
import org.jellyfin.androidtv.util.profile.ZidooPlayerProfile;
import org.jellyfin.androidtv.util.sdk.compat.ModelCompat;
import org.jellyfin.apiclient.interaction.ApiClient;
import org.jellyfin.apiclient.interaction.Response;
import org.jellyfin.apiclient.model.dlna.DlnaProfileType;
import org.jellyfin.apiclient.model.dlna.EncodingContext;
import org.jellyfin.apiclient.model.dlna.SubtitleDeliveryMethod;
import org.jellyfin.apiclient.model.dto.BaseItemDto;
import org.jellyfin.apiclient.model.dto.BaseItemType;
import org.jellyfin.apiclient.model.dto.MediaSourceInfo;
import org.jellyfin.apiclient.model.dto.UserItemDataDto;
import org.jellyfin.apiclient.model.session.PlayMethod;
import org.jellyfin.sdk.model.api.ImageFormat;
import org.jellyfin.sdk.model.api.ImageType;
import org.koin.java.KoinJavaComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.Lazy;
import timber.log.Timber;

public class ExternalPlayerActivity extends FragmentActivity {

    List<BaseItemDto> mItemsToPlay;
    BaseItemDto mCurrentItem;
    StreamInfo mCurrentStreamInfo;
    MediaSourceInfo mCurrentMediaSource;

    final Handler mHandler = new Handler();

    Runnable mReportLoop;
    static final int REPORT_LOOP_INTERVAL = 15000; // interval between playback report ticks
    static final int DURATION_10H_MS = 36000000; // 10h in ms

    int mBackdropHeight;

    long mLastPlayerStart = 0;
    int mSeekPosition = 0;
    String mVersionSourceId;
    boolean isLiveTv;
    boolean noPlayerError;
    boolean mUseSendPath = false;
    boolean mForceCompliantSurroundCodecs = false;
    boolean mAllowTranscoding = false;
    boolean mIsLibraryAudioPref = false;
    ZidooPlayerProfile mZidooProfile;
    AudioSubtitleHelper.AudioSubPref mPrefs;

    ZidooTask mZidooTask;
    TmdbTask mTmdbTask;

    final Lazy<ApiClient> apiClient = inject(ApiClient.class);
    final Lazy<UserPreferences> userPreferences = inject(UserPreferences.class);
    final Lazy<BackgroundService> backgroundService = inject(BackgroundService.class);
    final Lazy<MediaManager> mediaManager = inject(MediaManager.class);
    final Lazy<org.jellyfin.sdk.api.client.ApiClient> api = inject(org.jellyfin.sdk.api.client.ApiClient.class);
    final Lazy<PlaybackControllerContainer> playbackControllerContainer = inject(PlaybackControllerContainer.class);
    final Lazy<PreferencesRepository> preferencesRepository = inject(PreferencesRepository.class);

    // https://sites.google.com/site/mxvpen/api
    static final String API_MX_TITLE = "title";
    static final String API_MX_SEEK_POSITION = "position";
    static final String API_MX_FILENAME = "filename";
    static final String API_MX_RETURN_RESULT = "return_result";
    static final String API_MX_RESULT_ID = "com.mxtech.intent.result.VIEW";
    static final String API_MX_RESULT_POSITION = "position";
    static final String API_MX_RESULT_END_BY = "end_by";
    static final String API_MX_RESULT_END_BY_USER = "user";
    static final String API_MX_RESULT_END_BY_PLAYBACK_COMPLETION = "playback_completion";
    static final String API_MX_SUBS = "subs";
    static final String API_MX_SUBS_NAME = "subs.name";
    static final String API_MX_SUBS_FILENAME = "subs.filename";
    static final String API_MX_SUBS_ENABLE = "subs.enable";

    // https://wiki.videolan.org/Android_Player_Intents/
    static final String API_VLC_TITLE = "title";
    static final String API_VLC_SEEK_POSITION = "position";
    static final String API_VLC_FROM_START = "from_start";
    static final String API_VLC_RESULT_ID = "org.videolan.vlc.player.result";
    static final String API_VLC_RESULT_POSITION = "extra_position";

    // https://www.vimu.tv/player-api
    static final String API_VIMU_TITLE = "forcename";
    static final String API_VIMU_SEEK_POSITION = "startfrom";
    static final String API_VIMU_RESUME = "forceresume";
    static final String API_VIMU_RESULT_POSITION = "position";
    static final int API_VIMU_RESULT_PLAYBACK_COMPLETED = 1;
    static final int API_VIMU_RESULT_PLAYBACK_INTERRUPTED = 0;
    static final String API_VIMU_RESULT_ID = "net.gtvbox.videoplayer.result";

    // https://mpv-android.github.io/mpv-android/intent.html
    static final String API_MPV_RESULT_ID = "is.xyz.mpv.MPVActivity.result";

    // www.zidoo.tv
    static final int API_ZIDOO_REQUEST_CODE = 99;
    static final String API_ZIDOO_PACKAGE = "com.android.gallery3d";
    static final String API_ZIDOO_ACTIVITY_NAME_ZDMC = "com.android.gallery3d.app.ZDMCActivity";
    static final String API_ZIDOO_ACTIVITY_NAME_GALLERY = "com.android.gallery3d.app.GalleryActivity";
    static final String API_ZIDOO_ACTIVITY_NAME_MOVIE = "com.android.gallery3d.app.MovieActivity";
    static final String API_ZIDOO_SOURCEFROM = "SourceFrom";
    static final String API_ZIDOO_SOURCEFROM_LOCAL = "Local";
    static final String API_ZIDOO_PLAY_MODE = "play_mode";
    static final String API_ZIDOO_PLAY_MODE_ZDMC = "zdmc";
    static final String API_ZIDOO_NET_MODE = "net_mode";
    static final String API_ZIDOO_NET_MODE_LOCAL = "local";
    static final String API_ZIDOO_NET_MODE_SMB = "smb";
    static final String API_ZIDOO_NET_MODE_NFS = "nfs";
    static final String API_ZIDOO_NET_NFS_ROOT = "nfs_root";
    static final String API_ZIDOO_NET_SMB_USERNAME = "smb_username";
    static final String API_ZIDOO_NET_SMB_PASSWORD = "smb_password";
    static final String API_ZIDOO_PLAY_BROADCAST_STATUS = "android.intent.playback.broadcast.status";
    static final String API_ZIDOO_PLAY_USE_RT_MEDIA_PLAYER = "MEDIA_BROWSER_USE_RT_MEDIA_PLAYER";
    static final String API_ZIDOO_TITLE = "title";
    static final String API_ZIDOO_LOADING_IMAGE = "loading_image";
    static final String API_ZIDOO_CLEARLOGO_IMAGE = "clearlogo_image";
    static final String API_ZIDOO_TITLE_SUB_SEARCH = "subtitle_search_tilte";
    static final String API_ZIDOO_SEEK_POSITION = "position";
    static final String API_ZIDOO_DURATION = "duration";
    static final String API_ZIDOO_FROM_START = "from_start";
    static final String API_ZIDOO_PLAYMODEL = "playModel";
    static final String API_ZIDOO_AUDIO_IDX = "audio_idx";
    static final String API_ZIDOO_SUBTITLE_IDX = "subtitle_idx";
    static final String API_ZIDOO_RETURN_RESULT = "return_result";
    static final String API_ZIDOO_RESULT_END_BY = "end_by";
    static final String API_ZIDOO_RESULT_URL = "url";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Timber.d("onCreate");
        backgroundService.getValue().attach(this);

        if (!mediaManager.getValue().hasVideoQueueItems()) {
            Utils.showToast(this, getString(R.string.msg_no_playable_items));
            finish();
            return;
        }
        mBackdropHeight = getResources().getDisplayMetrics().heightPixels;
        mItemsToPlay = mediaManager.getValue().getCurrentVideoQueue();
        mSeekPosition = Math.max(getIntent().getIntExtra("Position", 0), 0);
        mVersionSourceId = getIntent().getStringExtra("VersionId"); // handle source Versions

        mUseSendPath = userPreferences.getValue().get(UserPreferences.Companion.getExternalVideoPlayerSendPath());
        mAllowTranscoding = userPreferences.getValue().get(UserPreferences.Companion.getEnableTranscodingFallback());
        mForceCompliantSurroundCodecs = userPreferences.getValue().get(UserPreferences.Companion.getForceBitstreamCompliantSurroundCodecs());

        mPrefs = new AudioSubtitleHelper.AudioSubPref(userPreferences);
        // handle folder lib settings
        String folderDispId = mediaManager.getValue().getFolderViewDisplayPreferencesId();
        if (isNonEmptyTrim(folderDispId)) {
            LibraryPreferences libraryPreferences = preferencesRepository.getValue().getLibraryPreferences(folderDispId);
            if (libraryPreferences.get(LibraryPreferences.Companion.getEnableAudioSettings())) {
                Timber.d("onCreate library override audioLang <%s> audioGlobal <%s>", libraryPreferences.get(LibraryPreferences.Companion.getAudioLanguage()), mPrefs.mAudioLangSetting);
                mPrefs.mAudioLangSetting = libraryPreferences.get(LibraryPreferences.Companion.getAudioLanguage());
                mIsLibraryAudioPref = true;
            }
        }

        String audioCodec = null;
        if (userPreferences.getValue().get(UserPreferences.Companion.getForcedAudioCodec()) != AudioCodecOut.NONE) {
            audioCodec = userPreferences.getValue().get(UserPreferences.Companion.getForcedAudioCodec()).getCodecName();
        }
        boolean forceStereo = false;
        if (!mForceCompliantSurroundCodecs) {
            forceStereo = userPreferences.getValue().get(UserPreferences.Companion.getForceStereo());
        }
//        if (Codec.Audio.MP3.equals(audioCodec)) {
//            forceStereo = true;
//        }
        mZidooProfile = new ZidooPlayerProfile(mPrefs.mHasDtsDecoder, mForceCompliantSurroundCodecs, audioCodec, forceStereo ? 2 : null);

        Timber.d("onCreate audio <%s> sub <%s>", mPrefs.mAudioLangSetting, mPrefs.mSubtitleLangSetting);

        prepareExternalPlayer(mItemsToPlay);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Timber.d("onNewIntent");
        resetPlayStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Timber.d("onDestroy");
        resetPlayStatus();
        if (!mediaManager.getValue().isVideoQueueModified()) mediaManager.getValue().clearVideoQueue();
    }

    // separate zidoo logic
    protected void onActivityResultZidoo(int resultCode, Intent data) {
        if (mZidooTask != null) {
            if (mZidooTask instanceof ZidooReportTask) {
                ((ZidooReportTask)mZidooTask).stop(data);
            } else {
                // something went wrong so just report the old seekPos
                ReportingHelper.reportStopped(mZidooTask.mItem, mZidooTask.mStreamInfo, mSeekPosition * RUNTIME_TICKS_TO_MS);
                mZidooTask.finishTask(false);
            }
        } else {
            Timber.e("Something went wrong, Zidoo code yet no ZidooTask?");
            finish();
            return;
        }

        // handle Zidoo player failures
        if (mZidooTask instanceof ZidooStartupTask && !((ZidooStartupTask) mZidooTask).mZidooStartupOK) {
            Timber.e("Zidoo startup failed, ignoring result!");
            if (mUseSendPath && mCurrentStreamInfo.getPlayMethod() == PlayMethod.DirectPlay) {
                handleZidooPlayerError();
            } else {
                finish();
            }
            return;
        }

        // Zidoo player told us the video played to the end -> continue with the next item
        if (!isLiveTv && mZidooTask instanceof ZidooReportTask && ((ZidooReportTask) mZidooTask).mPlaybackCompleted) {
            Timber.i("Detected playback completion for Zidoo player.");
            playNext();
            return;
        }

        Long runtime = mZidooTask.mStreamInfo.getRunTimeTicks() != null ? mZidooTask.mStreamInfo.getRunTimeTicks() : mCurrentItem.getOriginalRunTimeTicks();
        Integer pos = mZidooTask.mPlayPos;
        if (runtime == null || runtime <= 0 || pos == null || pos <= 0) {
            Timber.w("Could not get valid runtime, pos skipping playNext logic.");
            mItemsToPlay.remove(0);
            finish();
            return;
        }

        runtime = runtime / RUNTIME_TICKS_TO_MS;
        if (!isLiveTv && pos > (runtime * 0.9)) {
            playNext();
        } else {
            mItemsToPlay.remove(0);
            finish();
        }
    }

    @Nullable
    protected Integer getApiPlaybackPos(int resultCode, @Nullable Intent data) {
        int pos = -1;
        // look for result position in API's
        if (data != null) {
            if (data.hasExtra(API_MX_RESULT_POSITION)) {
                pos = data.getIntExtra(API_MX_RESULT_POSITION, -1);
            } else if (data.hasExtra(API_VLC_RESULT_POSITION)) {
                pos = data.getIntExtra(API_VLC_RESULT_POSITION, -1);
            } else if (data.hasExtra(API_VIMU_RESULT_POSITION)) {
                pos = data.getIntExtra(API_VIMU_RESULT_POSITION, -1);
            }
        }
        // check for playback completion in API's
        if (pos < 0 && data != null) {
            if (Objects.equals(data.getAction(), API_MX_RESULT_ID)) {
                if (resultCode == Activity.RESULT_OK && API_MX_RESULT_END_BY_PLAYBACK_COMPLETION.equals(data.getStringExtra(API_MX_RESULT_END_BY))) {
                    pos = DURATION_10H_MS;
                    Timber.i("Detected playback completion for MX player.");
                }
            } else if (Objects.equals(data.getAction(), API_VLC_RESULT_ID)) {
                if (resultCode == Activity.RESULT_OK) {
                    pos = DURATION_10H_MS;
                    Timber.i("Detected playback completion for VLC player.");
                }
            } else if (Objects.equals(data.getAction(), API_VIMU_RESULT_ID)) {
                if (resultCode == API_VIMU_RESULT_PLAYBACK_COMPLETED) {
                    pos = DURATION_10H_MS;
                    Timber.i("Detected playback completion for Vimu player.");
                }
            } else if (Objects.equals(data.getAction(), API_MPV_RESULT_ID)) {
                // mpv only omits the position when the file played to the end
                if (resultCode == Activity.RESULT_OK) {
                    pos = DURATION_10H_MS;
                    Timber.i("Detected playback completion for mpv player.");
                }
            }
        }
        if (pos >= 0) {
            return pos;
        } else {
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Timber.i("Returned from player request <%d>, result <%d>, extra data <%s>", requestCode, resultCode, data);
        if (requestCode == API_ZIDOO_REQUEST_CODE) {
            onActivityResultZidoo(resultCode, data);
            return;
        }
        long activityPlayTime = System.currentTimeMillis() - mLastPlayerStart;
        mLastPlayerStart = 0;
        stopReportLoop();
        if (mCurrentItem == null) {
            finish();
            return;
        }
        Integer pos = getApiPlaybackPos(resultCode, data);
        Timber.i("Player returned result position: <%s>", getMillisecondsFormated(pos));
        if (pos != null && pos > 0 && activityPlayTime > REPORT_LOOP_INTERVAL) {
            ReportingHelper.reportStopped(mCurrentItem, mCurrentStreamInfo, (long) pos * RUNTIME_TICKS_TO_MS);
        } else if (activityPlayTime > REPORT_LOOP_INTERVAL) {
            ReportingHelper.reportStopped(mCurrentItem, mCurrentStreamInfo, activityPlayTime * RUNTIME_TICKS_TO_MS); // use playtime as fallback
        } else {
            ReportingHelper.reportStopped(mCurrentItem, mCurrentStreamInfo, (long) mSeekPosition * RUNTIME_TICKS_TO_MS); // reset back to old seek position
        }
        if (activityPlayTime < REPORT_LOOP_INTERVAL) { // ignore "quick" play starts < than looptime
            Timber.i("Playback took less than <%s> seconds - ignoring.", REPORT_LOOP_INTERVAL / 1000);
            finish();
            return;
        }
        Long runtime = mCurrentItem.getRunTimeTicks() != null ? mCurrentItem.getRunTimeTicks() / RUNTIME_TICKS_TO_MS : null;
        if (runtime == null || runtime <= 0) {
            Timber.w("Could not get valid runtime, skipping playNext logic.");
            finish();
            return;
        }
        if (pos == null || pos == 0) {
            //If item didn't play as long as its duration - confirm we want to mark watched
            if (!isLiveTv && activityPlayTime < runtime * 0.9) {
                // only show dialog if played for at least 25%.
                if (activityPlayTime > runtime * 0.25) {
                    new AlertDialog.Builder(this).setTitle(R.string.mark_watched).setMessage(R.string.mark_watched_message).setPositiveButton(R.string.lbl_yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            markPlayed(mCurrentItem.getId());
                            playNext();
                        }
                    }).setNegativeButton(R.string.lbl_no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (!mediaManager.getValue().isVideoQueueModified()) {
                                mediaManager.getValue().clearVideoQueue();
                            } else {
                                mItemsToPlay.remove(0);
                            }
                            finish();
                        }
                    }).show();
                } else {
                    mItemsToPlay.remove(0);
                    finish();
                }
            } else {
                markPlayed(mCurrentItem.getId());
                playNext();
            }
        } else {
            if (!isLiveTv && pos > (runtime * 0.9)) {
                playNext();
            } else {
                mItemsToPlay.remove(0);
                finish();
            }
        }
    }

    private void resetPlayStatus() {
        stopReportLoop();
        if (mTmdbTask != null) {
            mTmdbTask.finishTask(true);
        }
        if (mZidooTask != null) {
            mZidooTask.finishTask(true);
        }
        mZidooTask = null;
        mCurrentItem = null;
        mCurrentStreamInfo = null;
        mCurrentMediaSource = null;
        mLastPlayerStart = 0;
        isLiveTv = false;
    }

    private void handleZidooPlayerError() {
        if (!mediaManager.getValue().isVideoQueueModified()) mediaManager.getValue().clearVideoQueue();

        new AlertDialog.Builder(this).setTitle(R.string.zidoo_player_error).setMessage(R.string.zidoo_player_error_message).setPositiveButton(R.string.btn_got_it, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        }).setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                finish();
            }
        }).show();
    }

    private void handleZidooPlayerNotFoundError() {
        if (!mediaManager.getValue().isVideoQueueModified()) mediaManager.getValue().clearVideoQueue();

        new AlertDialog.Builder(this).setTitle(R.string.zidoo_player_notfound).setMessage(R.string.zidoo_player_notfound_message).setPositiveButton(R.string.btn_got_it, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        }).setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                finish();
            }
        }).show();
    }

    private void handlePlayerError() {
        if (!mediaManager.getValue().isVideoQueueModified()) mediaManager.getValue().clearVideoQueue();

        new AlertDialog.Builder(this).setTitle(R.string.no_player).setMessage(R.string.no_player_message).setPositiveButton(R.string.btn_got_it, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        }).setNegativeButton(R.string.turn_off, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                userPreferences.getValue().set(UserPreferences.Companion.getVideoPlayer(), PreferredVideoPlayer.AUTO);
                userPreferences.getValue().set(UserPreferences.Companion.getLiveTvVideoPlayer(), PreferredVideoPlayer.AUTO);
            }
        }).setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                finish();
            }
        }).show();

    }

    private void startReportLoop() {
        PlaybackController playbackController = playbackControllerContainer.getValue().getPlaybackController();
        ReportingHelper.reportStart(mCurrentItem, (long) mSeekPosition * RUNTIME_TICKS_TO_MS);
        Timber.d("startReportLoop Position: %d ms, <%s>", mSeekPosition, getMillisecondsFormated(mSeekPosition));

        if (mCurrentStreamInfo.getPlayMethod() == PlayMethod.DirectStream || mCurrentStreamInfo.getPlayMethod() == PlayMethod.Transcode) {
            // TODO This runneable will run until next jellyfin app start, if the user hits "home" during external playback!
            mReportLoop = new Runnable() {
                @Override
                public void run() {
                    // TODO: is mPosition always 0 for external directpath players? Use activityPlayTime instead?
                    ReportingHelper.reportProgress(playbackController, mCurrentItem, mCurrentStreamInfo, null, false);
                    mHandler.postDelayed(this, REPORT_LOOP_INTERVAL);
                }
            };
            mHandler.postDelayed(mReportLoop, REPORT_LOOP_INTERVAL);
        }
    }

    private void stopReportLoop() {
        if (mHandler != null && mReportLoop != null) {
            mHandler.removeCallbacks(mReportLoop);
            mReportLoop = null;
        }
    }

    protected void markPlayed(String itemId) {
        apiClient.getValue().MarkPlayedAsync(itemId, KoinJavaComponent.<UserRepository>get(UserRepository.class).getCurrentUser().getValue().getId().toString(), null, new Response<UserItemDataDto>());
    }

    protected void playNext() {
        mItemsToPlay.remove(0);
        if (mItemsToPlay.size() > 0) {
            if (userPreferences.getValue().get(UserPreferences.Companion.getNextUpBehavior()) != NextUpBehavior.DISABLED) {
                // Set to "modified" so the queue won't be cleared
                mediaManager.getValue().setVideoQueueModified(true);

                Intent intent = new Intent(this, NextUpActivity.class);
                intent.putExtra(NextUpActivity.EXTRA_ID, mItemsToPlay.get(0).getId());
                intent.putExtra(NextUpActivity.EXTRA_USE_EXTERNAL_PLAYER, true);
                startActivity(intent);
                finishAfterTransition();
            } else {
                mVersionSourceId = null;
                mSeekPosition = 0;
                prepareExternalPlayer(mItemsToPlay);
            }
        } else {
            finish();
        }
    }

    @NonNull
    private VideoOptions buildZidooPlayerOptions(@Nullable Integer forcedAudioIndex, @Nullable Integer forcedSubtitleIndex) {
        VideoOptions internalOptions = new VideoOptions();
        internalOptions.setItemId(mCurrentItem.getId());
        internalOptions.setMediaSources(mCurrentItem.getMediaSources());
        internalOptions.setMediaSourceId(mCurrentMediaSource.getId());
        internalOptions.setMaxBitrate(Utils.getMaxBitrate());
        internalOptions.setProfile(mZidooProfile);
        if (forcedAudioIndex != null) {
            internalOptions.setAudioStreamIndex(forcedAudioIndex);
        }
        if (forcedSubtitleIndex != null) {
            internalOptions.setSubtitleStreamIndex(forcedSubtitleIndex);
        }

        return internalOptions;
    }

    // NOTE: We can get a item that's not fully filled with data!
    protected void prepareExternalPlayer(@NonNull List<BaseItemDto> itemList) {
        resetPlayStatus(); // reset globals

        BaseItemDto item = itemList.get(0);
        if (item == null || isEmptyTrim(item.getId())) {
            Timber.e("Invalid null item or no Id.");
            Utils.showToast(this, getString(R.string.msg_no_playable_items));
            finish();
            return;
        }

        // some items come with broken provider data
        boolean needsUpdate = mPrefs.mAudioLangSetting == LanguagesAudio.ORIGINAL && item.getProviderIds() == null;

        if (!needsUpdate && isNonEmptyTrim(item.getPath()) && isNonEmpty(item.getMediaStreams()) && isNonEmpty(item.getMediaSources())) {
            mCurrentItem = item;
            prepareLaunchExternalPlayer();
        } else {
            // try fix item, we need at least streams/path filled
            Timber.d("Incomplete data detected: item <%s> trying to refresh data.", item.getName());
            apiClient.getValue().GetItemAsync(item.getId(), KoinJavaComponent.<UserRepository>get(UserRepository.class).getCurrentUser().getValue().getId().toString(), new Response<BaseItemDto>() {
                @Override
                public void onResponse(BaseItemDto response) {
                    if (response != null && isNonEmptyTrim(response.getId()) && isNonEmptyTrim(response.getPath()) && isNonEmpty(response.getMediaStreams())) {
                        mCurrentItem = response;
                        prepareLaunchExternalPlayer();
                    } else {
                        Timber.e("launchExternalPlayer can't get playable item data!");
                        finish();
                    }
                }

                @Override
                public void onError(Exception exception) {
                    Timber.e(exception, "onError getting playable item data!");
                    finish();
                }
            });
        }
    }

    protected void prepareLaunchExternalPlayer() {
        mCurrentMediaSource = mCurrentItem.getMediaSources().get(0);
        // handle Versions and set correct streams
        if (isNonEmptyTrim(mVersionSourceId)) {
            MediaSourceInfo versionMediaSource = getMediaSourceById(mCurrentItem, mVersionSourceId);
            if (versionMediaSource != null) {
                mCurrentMediaSource = versionMediaSource;
            }
        }

        String orgLang = null;
        // try get org_language, only if needed
        int numLang = AudioSubtitleHelper.getNumAudioLang(mCurrentMediaSource.getMediaStreams());
        if (mPrefs.mAudioLangSetting == LanguagesAudio.ORIGINAL && numLang > 1) {
            if (mTmdbTask != null) { // check if we already have a good result (case: series playlist)
                orgLang = mTmdbTask.getOriginalLanguage(mCurrentItem.getId(), mCurrentItem.getSeriesId());
            }
            if (mTmdbTask == null || orgLang == null) {
                mTmdbTask = new TmdbTask(this, mCurrentItem, this::launchExternalPlayer);
                return; // TmdbTask will call launchExternalPlayer
            }
        }

        launchExternalPlayer();
    }

    @NonNull
    private StreamInfo buildStreamInfoSendPath() {
        StreamInfo info = new StreamInfo();
        info.setContext(EncodingContext.Static);
        info.setDeviceId(api.getValue().getDeviceInfo().getId());
        info.setMediaType(DlnaProfileType.Video);
        info.setPlayMethod(PlayMethod.DirectPlay);
        info.setItemId(mCurrentItem.getId());
        info.setMediaSource(mCurrentMediaSource);
        info.setRunTimeTicks(mCurrentMediaSource.getRunTimeTicks());
        info.setContainer(mCurrentMediaSource.getContainer());
        info.setStartPositionTicks(mSeekPosition * RUNTIME_TICKS_TO_MS);

        return info;
    }

    private final Pattern RegExTranscodeReasonsPattern = Pattern.compile("TranscodeReasons=([0-9a-zA-z]*)");

    @NonNull
    private String getTranscodeReason() {
        String reason = "";
        try {
            Matcher matcher = RegExTranscodeReasonsPattern.matcher(mCurrentStreamInfo.getMediaUrl());
            if (matcher.find()) {
                String found = matcher.group(1);
                if (isNonEmpty(found)) {
                    reason = found;
                }
            }
        } catch (Exception ignored) {
        }
        return reason;
    }

    private void showTranscodeInfoPopup() {
        if (!userPreferences.getValue().get(UserPreferences.Companion.getShowTranscodePlaybackPopup()))
            return;
        if (mCurrentStreamInfo.getPlayMethod() == PlayMethod.Transcode) {
            Utils.showToast(ExternalPlayerActivity.this, String.format(Locale.US, "%s\n\n%s\n%s",
                    getDisplayTitle(),
                    mCurrentStreamInfo.getPlayMethod().toString(),
                    getTranscodeReason()
                    )
            );
        }
    }

    protected void launchExternalPlayer() {
        isLiveTv = mCurrentItem.getBaseItemType() == BaseItemType.TvChannel;
        if (!isLiveTv && mUseSendPath && !mAllowTranscoding) {
            // build fake streamInfo so report logic can work
            mCurrentStreamInfo = buildStreamInfoSendPath();
            // Just pass the path directly
            Uri uri = getSendPathUri(mCurrentItem, mCurrentMediaSource);
            if (DeviceUtils.hasNewZidooApi()) {
                startExternalZidooMovieActivityDirectPath(uri);
            } else {
                startExternalZidooZDMCActivity(uri);
            }
        } else {
            String orgLang = mTmdbTask != null ? mTmdbTask.getOriginalLanguage(mCurrentItem.getId(), mCurrentItem.getSeriesId()) : null;
            Pair<Integer, Integer> audioSubIdx = getAbsoluteAudioSubIdxSafe(getBestAudioSubtitleIdx(mCurrentMediaSource.getMediaStreams(), mPrefs, orgLang));
            // Get playback info and then decide on which activity to start
            KoinJavaComponent.<PlaybackManager>get(PlaybackManager.class).getVideoStreamInfo(api.getValue().getDeviceInfo(), buildZidooPlayerOptions(audioSubIdx.first, audioSubIdx.second), mSeekPosition * RUNTIME_TICKS_TO_MS, apiClient.getValue(), new Response<StreamInfo>() {
                @Override
                public void onResponse(StreamInfo response) {
                    if (response == null || response.getMediaSource() == null || isEmpty(response.getMediaSource().getMediaStreams())) {
                        Timber.e("onResponse: Attempt to play a empty media item.");
                        finish();
                    } else {
                        mCurrentStreamInfo = response;
                        mCurrentMediaSource = response.getMediaSource();

                        showTranscodeInfoPopup();
                        if (mUseSendPath && mCurrentStreamInfo.getPlayMethod() == PlayMethod.DirectPlay) {
                            Uri uri = getSendPathUri(mCurrentItem, mCurrentMediaSource);
                            if (DeviceUtils.hasNewZidooApi()) {
                                startExternalZidooMovieActivityDirectPath(uri);
                            } else {
                                startExternalZidooZDMCActivity(uri);
                            }
                        } else {
                            Timber.i("URI: %s",Uri.parse(mCurrentStreamInfo.getMediaUrl()).normalizeScheme());
                            startExternalZidooMovieActivity(Uri.parse(mCurrentStreamInfo.getMediaUrl()).normalizeScheme());
                        }
                    }
                }

                @Override
                public void onError(Exception exception) {
                    Timber.e(exception, "onError getting playback stream info");
                    if (exception instanceof PlaybackException) {
                        switch (((PlaybackException) exception).getErrorCode()) {
                            case NotAllowed -> Utils.showToast(ExternalPlayerActivity.this, getString(R.string.msg_playback_not_allowed));
                            case NoCompatibleStream -> Utils.showToast(ExternalPlayerActivity.this, getString(R.string.msg_playback_incompatible));
                            case RateLimitExceeded -> Utils.showToast(ExternalPlayerActivity.this, getString(R.string.msg_playback_restricted));
                        }
                    }
                    finish();
                }
            });
        }
    }

    @Nullable
    public MediaSourceInfo getMediaSourceById(@NonNull BaseItemDto item, @NonNull String sourceId) {
        for (MediaSourceInfo source : item.getMediaSources()) {
            if (source.getId().equals(sourceId)) {
                return source;
            }
        }
        return null;
    }

    @NonNull
    protected static Uri getSendPathUri(@NonNull BaseItemDto baseItem, @Nullable MediaSourceInfo mediaSource) {
        String basePath = baseItem.getPath();
        if (!basePath.contains("://")) {
            basePath = basePath.replace("\\\\", ""); // remove UNC prefix if there
            //prefix with smb
            basePath = "smb://" + basePath;
        }
        basePath = basePath.replaceAll("\\\\", "/");
//        basePath = basePath.replace("/:", ""); // remove old NFS marker
        Uri uri = Uri.parse(basePath).normalizeScheme();
        // handle broken network paths for none [0] sources
        if (mediaSource != null && isNonEmptyTrim(mediaSource.getPath()) && !baseItem.getId().equals(mediaSource.getId())) {
            try {
                // extract filename and build new path, assumes versions are always in the same directory with the main source
                String extraPath = mediaSource.getPath();
                extraPath = extraPath.replaceAll("\\\\", "/");
                String fileName = extraPath.substring(extraPath.lastIndexOf('/') + 1);
                String newPath = basePath.substring(0, basePath.lastIndexOf('/') + 1).concat(fileName);
                uri = Uri.parse(newPath).normalizeScheme();
            } catch (Exception ignored) {
            }
        }

        return uri;
    }

    @Nullable
    protected String getDisplayTitle() {
        // build full title string
        String full_title = "";
        Context context = getBaseContext();
        if (context != null) {
            full_title = BaseItemUtils.getDisplayName(mCurrentItem, context);
        }
        if (isEmptyTrim(full_title)) {
            full_title = mCurrentItem.getName();
        }
        // handle versions
        if (!mCurrentItem.getId().equals(mCurrentMediaSource.getId())) {
            full_title += " [" + mCurrentMediaSource.getName() + "]";
        }
        if (mCurrentItem.getProductionYear() != null && mCurrentItem.getProductionYear() > 0) {
            full_title += " - (" + mCurrentItem.getProductionYear() + ")";
        }
        if (isNonEmptyTrim(full_title)) {
            return full_title;
        } else {
            return null;
        }
    }

//    @NonNull
//    protected static String getContainer(@NonNull MediaSourceInfo source, @NonNull StreamInfo streamInfo) {
//        String container = "*";
//        if (streamInfo.getPlayMethod() == PlayMethod.DirectPlay) {
//            if (isNonEmptyTrim(source.getContainer())) {
//                container = source.getContainer();
//            }
//        } else if (streamInfo.getMediaSource() != null && isNonEmptyTrim(streamInfo.getMediaSource().getContainer())) {
//            container = streamInfo.getMediaSource().getContainer();
//        }
//
//        return container;
//    }

//    protected void startExternalActivity(String path, String container) {
//        Intent external = new Intent(Intent.ACTION_VIEW);
//        external.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//        external.setDataAndType(Uri.parse(path), "video/" + container);
//
//        String full_title = getDisplayTitle(mCurrentItem);
//        //Start player API params
//        external.putExtra(API_MX_SEEK_POSITION, mSeekPosition);
//        external.putExtra(API_VIMU_SEEK_POSITION, mSeekPosition);
//        if (mSeekPosition == 0) {
//            external.putExtra(API_VLC_FROM_START, true);
//        }
//        external.putExtra(API_VIMU_RESUME, false);
//        external.putExtra(API_MX_RETURN_RESULT, true);
//        if (isNonEmptyTrim(full_title)) {
//            external.putExtra(API_MX_TITLE, full_title);
//            external.putExtra(API_VIMU_TITLE, full_title);
//        }
//        String filepath = mCurrentItem.getPath();
//        if (isNonEmptyTrim(filepath)) {
//            File file = new File(filepath);
//            if (isNonEmptyTrim(file.getName())) {
//                external.putExtra(API_MX_FILENAME, file.getName());
//            }
//        }
//        //End player API params
//
//        Timber.i("Starting external playback of path: %s and mime: video/%s at position: %d ms, <%s>", path, container, mSeekPosition, getMillisecondsFormated(mSeekPosition));
//
//        try {
//            mLastPlayerStart = System.currentTimeMillis();
//            mHandler.postDelayed(this::startReportLoop, 5000); // only start reports after we give the external player enough time to start, otherwise reports are invalid
//            startActivityForResult(external, 1);
//        } catch (ActivityNotFoundException e) {
//            noPlayerError = true;
//            Timber.e(e, "Error launching external player");
//            finish();
//            //            handlePlayerError();
//        }
//    }

    private void startExternalZidooZDMCActivity(@NonNull Uri pathUri) {
        if (!"smb".equals(pathUri.getScheme()) && !"nfs".equals(pathUri.getScheme())) {
            Utils.showToast(ExternalPlayerActivity.this, "Error: Unsupported mount path Uri-Scheme!");
            Timber.e("Error Uri is not a smb/nfs Path <%s>!", pathUri.toString());
            finish();
            return;
        }
        Intent zidooIntent = new Intent(Intent.ACTION_VIEW);
        zidooIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        zidooIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        zidooIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        //        zidooIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        //        zidooIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        zidooIntent.setPackage(API_ZIDOO_PACKAGE);
        zidooIntent.setClassName(API_ZIDOO_PACKAGE, API_ZIDOO_ACTIVITY_NAME_ZDMC);
        zidooIntent.putExtra(API_ZIDOO_PLAY_MODE, API_ZIDOO_PLAY_MODE_ZDMC);
        //        String netMode = API_ZIDOO_NET_MODE_LOCAL;

        if ("smb".equals(pathUri.getScheme())) {
            zidooIntent.putExtra(API_ZIDOO_NET_MODE, API_ZIDOO_NET_MODE_SMB);
            Timber.i("Using SMB NET_MODE via ZDMCActivity!");
            Pair<String, String> smbUserPass = PlayerApiHelpers.getSmbUserPass(pathUri);
            if (isNonEmptyTrim(smbUserPass.first)) {
                zidooIntent.putExtra(API_ZIDOO_NET_SMB_USERNAME, smbUserPass.first);
                Timber.i("Using SMB username <%s> for ZDMCActivity!", smbUserPass.first);
            }
            if (isNonEmptyTrim(smbUserPass.second)) {
                zidooIntent.putExtra(API_ZIDOO_NET_SMB_PASSWORD, smbUserPass.second);
                Timber.i("Using SMB password ******* for ZDMCActivity!");
            }
        } else if ("nfs".equals(pathUri.getScheme())) {
            zidooIntent.putExtra(API_ZIDOO_NET_MODE, API_ZIDOO_NET_MODE_NFS);
            Timber.i("Using NFS NET_MODE via ZDMCActivity!");
            Pair<String, String> nfsRootShare = PlayerApiHelpers.getNfsRoot(pathUri);
            pathUri = Uri.parse(pathUri.toString().replace("/:", "")).normalizeScheme(); // fix Uri
            zidooIntent.putExtra(API_ZIDOO_NET_NFS_ROOT, nfsRootShare.first);
            Timber.d("Using NFS root_path <%s> for ZDMCActivity!", nfsRootShare.first);
        }

        String container = mCurrentStreamInfo.getContainer();
        Timber.i("Starting external ZDMCActivity playback from <%s> at position <%s> ms, <%s> with path <%s> ", pathUri.getHost(), mSeekPosition, getMillisecondsFormated(mSeekPosition), pathUri.getPath());

        zidooIntent.setDataAndType(pathUri, "video/" + container);
        try {
            mLastPlayerStart = System.currentTimeMillis();
            mZidooTask = new ZidooStartupTask(this, mPrefs, mCurrentItem, mSeekPosition, mCurrentStreamInfo, 2000, ()-> {
                mZidooTask = new ZidooReportTask(mZidooTask, mSeekPosition, 5000); // HACK delay more to make subtitle selection stick at Zidoo "Auto" settings
                mZidooTask.SetTmdbLang(mTmdbTask);
            });
            startActivityForResult(zidooIntent, API_ZIDOO_REQUEST_CODE); // NOTE: ZDMCActivity is just a wrapper for MovieActivity and will finish() directly, while not sending any results!
        } catch (ActivityNotFoundException e) {
            noPlayerError = true;
            Timber.e(e, "Error launching external Zidoo player");
            handleZidooPlayerNotFoundError();
        }
    }

    private void startExternalZidooMovieActivityDirectPath(@NonNull Uri pathUri) {
        if (!"smb".equals(pathUri.getScheme()) && !"nfs".equals(pathUri.getScheme())) {
            Utils.showToast(ExternalPlayerActivity.this, "Error: Unsupported mount path Uri-Scheme!");
            Timber.e("Error Uri is not a smb/nfs Path <%s>!", pathUri.toString());
            finish();
            return;
        }

        if ("nfs".equals(pathUri.getScheme())) {
            pathUri = Uri.parse(pathUri.toString().replace("/:", "")).normalizeScheme(); // fix Uri
        }
        startExternalZidooMovieActivity(pathUri);

//        new MountTask(this, pathUri, new Response<>() {
//            @Override
//            public void onResponse(String response) {
//                if (response != null) {
//                    Timber.i("MountTask successfully with Path <%s>", response);
//                    try {
//                        Uri uri = Uri.fromFile(new File(response));
//                        startExternalZidooMovieActivity(uri);
//                    } catch (Exception e) {
//                        Timber.e("Error could not get Uri.fromFile <%s>", response);
//                        e.printStackTrace();
//                        finish();
//                    }
//                } else {
//                    Timber.e("Error could not mount Path <%s>", pathUri.toString());
//                    Utils.showToast(ExternalPlayerActivity.this, "Error: Could not mount path!");
//                    finish();
//                }
//            }
//        });
    }

    /**
     * Subtitle support for the Zidoo player (Andy2244/jellyfin-androidtv-zidoo#10):
     * - Online subtitle search ("Download subtitle") uses the file name, which is just "stream" for http playback.
     *   Pass the real title via Zidoo's subtitle search extra and the original file name via the MX "filename" extra.
     * - External subtitle files (side-car .srt/.ass next to the movie) are invisible to the player when it plays an
     *   http stream. Pass their Jellyfin delivery urls using the MX Player intent API ("subs"), which the Zidoo
     *   player's intent API mirrors (title/position/return_result/end_by).
     */
    private void addZidooSubtitleExtras(@NonNull Intent intent, @NonNull Uri pathUri) {
        try {
            String searchTitle = getSubtitleSearchTitle();
            if (isNonEmptyTrim(searchTitle)) {
                intent.putExtra(API_ZIDOO_TITLE_SUB_SEARCH, searchTitle);
            }
            String fileName = getOriginalFileName();
            if (isNonEmptyTrim(fileName)) {
                intent.putExtra(API_MX_FILENAME, fileName);
            }

            // Direct path (smb/nfs): the player sees the side-car files itself
            String scheme = pathUri.getScheme();
            if (scheme == null || !scheme.toLowerCase(Locale.ROOT).startsWith("http")) return;
            // Transcoding embeds/burns in the selected subtitle
            if (mCurrentStreamInfo == null || mCurrentStreamInfo.getPlayMethod() == PlayMethod.Transcode) return;
            if (mCurrentStreamInfo.getDeviceProfile() == null || mCurrentStreamInfo.getMediaSource() == null) return;

            ArrayList<SubtitleStreamInfo> subtitles = mCurrentStreamInfo.GetSubtitleProfiles(false, apiClient.getValue().getApiUrl(), apiClient.getValue().getAccessToken());
            List<Uri> uris = new ArrayList<>();
            List<String> names = new ArrayList<>();
            List<String> fileNames = new ArrayList<>();
            String baseName = fileName != null && fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : (fileName != null ? fileName : "subtitle");
            for (SubtitleStreamInfo subtitle : subtitles) {
                if (subtitle.getDeliveryMethod() != SubtitleDeliveryMethod.External) continue;
                String url = subtitle.getUrl();
                if (!isNonEmptyTrim(url) || !url.toLowerCase(Locale.ROOT).startsWith("http")) continue;
                uris.add(Uri.parse(url));
                String name = isNonEmptyTrim(subtitle.getDisplayTitle()) ? subtitle.getDisplayTitle() : subtitle.getName();
                names.add(name);
                String lang = isNonEmptyTrim(subtitle.getName()) && !"Unknown".equals(subtitle.getName()) ? "." + subtitle.getName() : "";
                fileNames.add(baseName + lang + "." + subtitle.getIndex() + "." + subtitle.getFormat());
            }
            if (uris.isEmpty()) return;

            intent.putExtra(API_MX_SUBS, uris.toArray(new Uri[0]));
            intent.putExtra(API_MX_SUBS_NAME, names.toArray(new String[0]));
            intent.putExtra(API_MX_SUBS_FILENAME, fileNames.toArray(new String[0]));
            Timber.i("Passing <%d> external subtitles to Zidoo player: %s", uris.size(), names);
        } catch (Exception e) {
            Timber.w(e, "Unable to add subtitle extras for Zidoo player");
        }
    }

    @Nullable
    private String getSubtitleSearchTitle() {
        if (mCurrentItem == null) return null;
        if (mCurrentItem.getBaseItemType() == BaseItemType.Episode && isNonEmptyTrim(mCurrentItem.getSeriesName())) {
            String title = mCurrentItem.getSeriesName();
            if (mCurrentItem.getParentIndexNumber() != null && mCurrentItem.getIndexNumber() != null) {
                title += String.format(Locale.US, " S%02dE%02d", mCurrentItem.getParentIndexNumber(), mCurrentItem.getIndexNumber());
            }
            return title;
        }
        String title = mCurrentItem.getName();
        if (isNonEmptyTrim(title) && mCurrentItem.getProductionYear() != null && mCurrentItem.getProductionYear() > 0) {
            title += " " + mCurrentItem.getProductionYear();
        }
        return title;
    }

    @Nullable
    private String getOriginalFileName() {
        String path = mCurrentMediaSource != null && isNonEmptyTrim(mCurrentMediaSource.getPath()) ? mCurrentMediaSource.getPath() : mCurrentItem.getPath();
        if (!isNonEmptyTrim(path)) return null;
        path = path.replace('\\', '/');
        String name = path.substring(path.lastIndexOf('/') + 1);
        return isNonEmptyTrim(name) ? name : null;
    }

    private void startExternalZidooMovieActivity(@NonNull Uri pathUri) {
        Intent zidooIntent = new Intent(Intent.ACTION_VIEW);
//        zidooIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
//        zidooIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        zidooIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        zidooIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
//        zidooIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        zidooIntent.setPackage(API_ZIDOO_PACKAGE);
        zidooIntent.setClassName(API_ZIDOO_PACKAGE, API_ZIDOO_ACTIVITY_NAME_MOVIE);

        // add loading background
        zidooIntent.putExtra(API_ZIDOO_LOADING_IMAGE, KoinJavaComponent.<ImageHelper>get(ImageHelper.class).getImageUrl(ModelCompat.asSdk(mCurrentItem), ImageType.BACKDROP, ImageFormat.WEBP,16, mBackdropHeight));
        String logoUrl = KoinJavaComponent.<ImageHelper>get(ImageHelper.class).getImageUrl(ModelCompat.asSdk(mCurrentItem), ImageType.LOGO, true, null, null, true, true, true);
        if (logoUrl != null) {
            zidooIntent.putExtra(API_ZIDOO_CLEARLOGO_IMAGE, logoUrl);
        }
        String full_title = getDisplayTitle();
        if (full_title != null) {
            zidooIntent.putExtra(API_ZIDOO_TITLE, full_title);
        }

        if (mSeekPosition > 0) {
            zidooIntent.putExtra(API_ZIDOO_FROM_START, false);
            zidooIntent.putExtra(API_ZIDOO_SEEK_POSITION, mSeekPosition);
        } else {
            zidooIntent.putExtra(API_ZIDOO_FROM_START, true);
        }
        if (mPrefs.mAudioLangSetting != LanguagesAudio.DEVICE) {
            String orgLang = mTmdbTask != null ? mTmdbTask.getOriginalLanguage(mCurrentItem.getId(), mCurrentItem.getSeriesId()) : null;
            Pair<Integer, Integer> audioSubIdx = ZidooTask.convertToZidooIndex(getBestAudioSubtitleIdx(mCurrentStreamInfo.getMediaSource().getMediaStreams(), mPrefs, orgLang));
            if (mCurrentStreamInfo.getPlayMethod() == PlayMethod.Transcode) {
                int subIdx = audioSubIdx.second > 0 ? 1 : 0; // we have only one sub here
                zidooIntent.putExtra(API_ZIDOO_AUDIO_IDX, 0); // only one track
                zidooIntent.putExtra(API_ZIDOO_SUBTITLE_IDX, subIdx);
                Timber.d("Intent audioIdx <%s> subIdx <%s>", 0, subIdx);
            } else {
                zidooIntent.putExtra(API_ZIDOO_AUDIO_IDX, audioSubIdx.first);
                zidooIntent.putExtra(API_ZIDOO_SUBTITLE_IDX, audioSubIdx.second); // always set, so "off" works correctly vs Zidoo Bookmarks logic
                Timber.d("Intent audioIdx <%s> subIdx <%s>", audioSubIdx.first, audioSubIdx.second);
            }
        }
        zidooIntent.putExtra(API_ZIDOO_RETURN_RESULT, true);
        addZidooSubtitleExtras(zidooIntent, pathUri);

        String container = mCurrentStreamInfo.getContainer();
        zidooIntent.setDataAndType(pathUri, "video/" + container);
        Timber.i("Starting external MovieActivity playback <%s> from <%s> at position <%s> ms, <%s> with path <%s>", mCurrentStreamInfo.getPlayMethod().toString(), pathUri.getHost(), mSeekPosition, getMillisecondsFormated(mSeekPosition), pathUri.getPath());

        try {
            mLastPlayerStart = System.currentTimeMillis();
            mZidooTask = new ZidooStartupTask(this, mPrefs, mCurrentItem, mSeekPosition, mCurrentStreamInfo, 2000, ()-> {
                mZidooTask = new ZidooReportTask(mZidooTask, mSeekPosition, 5000); // HACK delay more to make subtitle selection stick at Zidoo "Auto" settings
                mZidooTask.SetTmdbLang(mTmdbTask);
            });
            startActivityForResult(zidooIntent, API_ZIDOO_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            noPlayerError = true;
            Timber.e(e, "Error launching external Zidoo player");
            handleZidooPlayerNotFoundError();
        }
    }
}
