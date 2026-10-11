package org.jellyfin.androidtv.ui.playback;

import static org.jellyfin.androidtv.ui.playback.PlayerApiHelpers.API_ZIDOO_HTTP_API_VIDEOPLAY_STATUS_ERROR;
import static org.jellyfin.androidtv.ui.playback.PlayerApiHelpers.API_ZIDOO_HTTP_API_VIDEOPLAY_STATUS_PAUSE;
import static org.jellyfin.androidtv.ui.playback.PlayerApiHelpers.API_ZIDOO_HTTP_API_VIDEOPLAY_STATUS_PLAYING;
import static org.jellyfin.androidtv.ui.playback.PlayerApiHelpers.SUBTITLE_DISABLED;
import static org.jellyfin.androidtv.ui.playback.PlayerApiHelpers.getFromTmdbHttp_API;
import static org.jellyfin.androidtv.ui.playback.PlayerApiHelpers.getZidooPlayStatusEx;
import static org.jellyfin.androidtv.ui.playback.PlayerApiHelpers.setZidooAudioTrack;
import static org.jellyfin.androidtv.ui.playback.PlayerApiHelpers.setZidooSeekPosition;
import static org.jellyfin.androidtv.ui.playback.PlayerApiHelpers.setZidooSubtitleTrack;
import static org.jellyfin.androidtv.util.AudioSubtitleHelper.getBestAudioSubtitleIdx;
import static org.jellyfin.androidtv.util.Utils.RUNTIME_TICKS_TO_MS;
import static org.jellyfin.androidtv.util.Utils.getMillisecondsFormated;
import static org.jellyfin.androidtv.util.Utils.isNonEmptyTrim;
import static org.koin.java.KoinJavaComponent.inject;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.auth.repository.UserRepository;
import org.jellyfin.androidtv.preference.UserPreferences;
import org.jellyfin.androidtv.preference.constant.SegmentSkipBehavior;
import org.jellyfin.androidtv.data.compat.StreamInfo;
import org.jellyfin.androidtv.preference.constant.LanguagesAudio;
import org.jellyfin.androidtv.util.AudioSubtitleHelper;
import org.jellyfin.androidtv.util.apiclient.ReportingHelper;
import org.jellyfin.apiclient.interaction.ApiClient;
import org.jellyfin.apiclient.interaction.Response;
import org.jellyfin.apiclient.model.dto.BaseItemDto;
import org.jellyfin.apiclient.model.dto.BaseItemType;
import org.jellyfin.apiclient.model.entities.MediaStream;
import org.jellyfin.apiclient.model.session.PlayMethod;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.koin.java.KoinJavaComponent;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kotlin.Lazy;
import timber.log.Timber;

abstract class PlayerTask implements Runnable {
    final protected Lazy<ApiClient> apiClient = inject(ApiClient.class);
    private HandlerThread mHandlerThread = null;
    private Handler mTaskHandler = null;
    protected boolean mIsFinished = false;
    final public long mActivityStartTime;
    protected Long mActivityStopTime = null;
    final protected Activity mActivity;

    protected PlayerTask(@NonNull Activity activity) {
        try {
            mHandlerThread = new HandlerThread(this.getClass().getSimpleName());
            mHandlerThread.start();
            mTaskHandler = new Handler(mHandlerThread.getLooper());
        } catch (Exception e) {
            e.printStackTrace();
            activity.finish();
        }
        mActivityStartTime = System.currentTimeMillis();
        mActivity = activity;
        Timber.d("New PlayerTask: <%s>", this.getClass().getName());
    }

    protected void finishTask(boolean isReset) {
        if (mActivityStopTime == null) {
            mActivityStopTime = System.currentTimeMillis();
        }
        mIsFinished = true;
        if (mTaskHandler != null) {
            mTaskHandler.removeCallbacks(this);
        }
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread.interrupt();
        }
        mHandlerThread = null;
        mTaskHandler = null;
    }

    public int getRuntime() {
        if (mActivityStopTime != null) {
            return (int) (mActivityStopTime - mActivityStartTime);
        } else {
            return (int) (System.currentTimeMillis() - mActivityStartTime);
        }
    }

    protected boolean post(Runnable runnable, int delay) {
        if (!mIsFinished && mTaskHandler != null && runnable != null) {
            return mTaskHandler.postDelayed(runnable, delay);
        }
        return false;
    }
}

//class MountTask extends PlayerTask {
//    final Response<String> mCallback;
//    final String mInputPath;
//    String mShareName;
//    String mServerHostName;
//    String mUserName;
//    String mPassword;
//    String mMountPath;
//    String mRelativePath;
//    boolean isNfs;
//
//    public MountTask(@NonNull final Activity activity, @NonNull Uri pathUri, @NonNull final Response<String> callback) {
//        super(activity);
//        mCallback = callback;
//        mMountPath = null;
//        mShareName = null;
//        mServerHostName = null;
//        mUserName = null;
//        mPassword = null;
//        mRelativePath = null;
//
//        mInputPath = pathUri.getPath();
//        if (isNonEmptyTrim(pathUri.getHost())) {
//            mServerHostName = pathUri.getHost();
//        }
//        Timber.d("MountTask with host <%s> path <%s>", mServerHostName, mInputPath);
//        if ("smb".equals(pathUri.getScheme()) && isNonEmptyTrim(mInputPath)) {
//            if (isNonEmpty(pathUri.getPathSegments())) {
//                mShareName = pathUri.getPathSegments().get(0);
//            }
//            mRelativePath = mInputPath.replaceFirst("/" + mShareName,""); // FIXME needs tobe the first / anchor?
//            Pair<String, String> smbUserPass = getSmbUserPass(pathUri);
//            mUserName = smbUserPass.first;
//            mPassword = smbUserPass.second;
//            Timber.d("MountTask using SMB username <%s>", mUserName);
//            if (mPassword != null) {
//                Timber.d("MountTask using SMB password <*******>");
//            }
//        } else if ("nfs".equals(pathUri.getScheme()) && isNonEmptyTrim(mInputPath)) {
//            isNfs = true;
//            Pair<String, String> nfsRootShare = getNfsRoot(pathUri);
//            mShareName = nfsRootShare.second;
//            mRelativePath = mInputPath.replace("/:", ""); // remove old NFS marker
//            mRelativePath = mRelativePath.replaceFirst("/" + nfsRootShare.second,""); // FIXME needs tobe the first / anchor?
//        }
//        if (mServerHostName == null || mShareName == null) {
//            Timber.e("MountTask invalid HostName/ShareName, ending!");
//            finishTask(false);
//        } else {
//            post(this, 0);
//        }
//    }
//
//    private void handleCallback() {
//        if (isNonEmptyTrim(mMountPath)) {
//            Timber.d("Using mountPath <%s> rPath <%s>", mMountPath, mRelativePath);
//            mCallback.onResponse(mMountPath + mRelativePath);
//        } else {
//            mCallback.onResponse(null);
//        }
//    }
//
//    @Override
//    protected void finishTask(boolean isReset) {
//        if (!mIsFinished) {
//            super.finishTask(isReset);
//            if (!isReset) {
//                mActivity.runOnUiThread(this::handleCallback); // run in Ui thread!
//            }
//        }
//    }
//
//    @Override
//    public void run() {
//        String ip = mServerHostName;
//        try {
//            // NOTE: new API has no hostname support
//            Address addr = jcifs.context.SingletonContext.getInstance().getNameServiceClient().getByName(mServerHostName);
//            ip = addr.getHostAddress();
//        } catch (Exception ignored) {
//        } finally {
//            try {
//                jcifs.context.SingletonContext.getInstance().close();
//            } catch (CIFSException ignored) {
//            }
//        }
//
//        ZEMountManage mZEMountManage = new ZEMountManage(mActivity);
//        if (isNfs) {
//            Timber.i("MountTask NFS using Host-IP <%s> Share <%s>", ip, mShareName);
//            mMountPath = mZEMountManage.mountNfs(mShareName, ip);
//        } else {
//            Timber.i("MountTask SMB using Host-IP <%s> Share <%s> User <%s>", ip, mShareName, mUserName);
//            mMountPath = mZEMountManage.mountSmb(mShareName, ip, mUserName, mPassword);
//        }
//        //callback mount  AbsolutePath
//        //  as: /data/system/smb/192.168.11.106#zidoo
//        finishTask(false);
//    }
//}

class TmdbTask extends PlayerTask {
    static final long MAX_TMDB_TASK_TIME_MS = 10000;
    final public long mActivityStartTime;
    public BaseItemDto mParentItem;
    final public BaseItemDto mItem;
    private String mOriginalLanguageTmdb;
    private final Runnable mCallback;

    public TmdbTask(@NonNull final Activity activity, @NonNull BaseItemDto item, @NonNull final Runnable callback) {
        super(activity);
        mActivityStartTime = System.currentTimeMillis();
        mItem = item;
        mParentItem = null;
        mOriginalLanguageTmdb = null;
        mCallback = callback;
        post(this, 0); // start
    }

    @Override
    protected void finishTask(boolean isReset) {
        if (!mIsFinished) {
            super.finishTask(isReset);
            if (mOriginalLanguageTmdb != null) {
                if (mParentItem != null) {
                    Timber.d("TmdbTask <%s> success: id <%s> org_langauge <%s>", mParentItem.getBaseItemType().toString(), mParentItem.getName(), mOriginalLanguageTmdb);
                } else {
                    Timber.d("TmdbTask <%s> success: id <%s> org_langauge <%s>", mItem.getBaseItemType().toString(), mItem.getName(), mOriginalLanguageTmdb);
                }
            }
            if (!isReset) {
                mActivity.runOnUiThread(mCallback); // make sure its called on Ui thread
            }
        }
    }

    @Override
    public void run() {
        // Safeguard against recursive runs
        long activityRunTime = System.currentTimeMillis() - mActivityStartTime;
        if (activityRunTime > MAX_TMDB_TASK_TIME_MS) {
            finishTask(false);
            return;
        }
        BaseItemDto checkItem = mItem;
        if (mParentItem != null) {
            checkItem = mParentItem;
        }
        if (checkItem.getBaseItemType() == BaseItemType.Episode && isNonEmptyTrim(checkItem.getSeriesId())) {
            apiClient.getValue().GetItemAsync(checkItem.getSeriesId(), KoinJavaComponent.<UserRepository>get(UserRepository.class).getCurrentUser().getValue().getId().toString(), new Response<BaseItemDto>() {
                // NOTE: this runs in mainThread!
                @Override
                public void onResponse(BaseItemDto response) {
                    if (response.getBaseItemType() == BaseItemType.Series && response.getProviderIds() != null) {
                        if (response.getProviderIds().containsKey("Tmdb") || response.getProviderIds().containsKey("Tvdb")) {
                            mParentItem = response;
                            post(TmdbTask.this, 0);
                        }
                    }
                }
                @Override
                public void onError(Exception exception) {
                    finishTask(false);
                }
            });
        } else if (checkItem.getBaseItemType() == BaseItemType.Movie && checkItem.getProviderIds() != null && checkItem.getProviderIds().containsKey("Tmdb")) {
            JSONObject tmdb_obj = getFromTmdbHttp_API("movie", checkItem.getProviderIds().get("Tmdb"), null, null);
            if (tmdb_obj != null && tmdb_obj.has("original_language")) {
                mOriginalLanguageTmdb = tmdb_obj.optString("original_language");
            }
            finishTask(false);
        } else if (checkItem.getBaseItemType() == BaseItemType.Series && checkItem.getProviderIds() != null) {
            if (checkItem.getProviderIds().containsKey("Tmdb")) {
                JSONObject tmdb_obj = getFromTmdbHttp_API("tv", checkItem.getProviderIds().get("Tmdb"), null, null);
                if (tmdb_obj != null && tmdb_obj.has("original_language")) {
                    mOriginalLanguageTmdb = tmdb_obj.optString("original_language");
                }
            } else if (checkItem.getProviderIds().containsKey("Tvdb")) {
                JSONObject tmdb_find_obj = getFromTmdbHttp_API("find", checkItem.getProviderIds().get("Tvdb"), "external_source=tvdb_id", null);
                if (tmdb_find_obj != null && tmdb_find_obj.has("tv_results")) {
                    try {
                        JSONArray tv_results = tmdb_find_obj.getJSONArray("tv_results");
                        if (tv_results.length() > 0 && tv_results.getJSONObject(0) != null) {
                            JSONObject tv_results_entry = tv_results.getJSONObject(0);
                            if (tv_results_entry != null && tv_results_entry.has("original_language")) {
                                mOriginalLanguageTmdb = tv_results_entry.optString("original_language");
                            }
                        }
                    } catch (JSONException ignored) {
                    }
                }
            }
            finishTask(false);
        } else {
            finishTask(false);
        }
    }

    @Nullable
    public String getOriginalLanguage(@Nullable String id, @Nullable String parentId) {
        if (isNonEmptyTrim(id) && mItem != null && mItem.getId().equals(id)) {
            return mOriginalLanguageTmdb;
        }
        if (isNonEmptyTrim(parentId) && mParentItem != null && mParentItem.getId().equals(parentId)) {
            return mOriginalLanguageTmdb;
        }
        Timber.w("getOriginalLanguage id's don't match!");
        return null;
    }
}

abstract class ZidooTask extends PlayerTask {
    static final int API_ZIDOO_STARTUP_TIMEOUT = 20000; // allow Zidoo player to trigger wake + hdd spinnup + smb mount and start playback
    static final int API_ZIDOO_STARTUP_RETRY_INTERVAL = 400; // interval between startup detection try's
    static final int API_ZIDOO_HTTP_API_REPORT_LOOP_INTERVAL = 15000; // interval between playback report ticks
    static final int API_ZIDOO_HTTP_API_MAX_ERROR_COUNT = 5; // maximum http errors, before we fail
    static final int API_ZIDOO_SEEKPOS_DELTA = 10000; // delta in ms when to seek vs current pos

    final public BaseItemDto mItem;
    final public StreamInfo mStreamInfo;
    final protected AudioSubtitleHelper.AudioSubPref mPrefs;
    public Pair<Integer, Integer> bestAudioSubIdxZidoo;
    protected Integer mCurrentAudioIdx;
    protected Integer mCurrentSubIdx;
    protected Integer mZidooIdentifierHashStartup;
    protected Integer mZidooIdentifierHash;
    public Integer mPlayPos;
    protected PlayMethod mPlayMethod;
    protected int mPlayStatus;
    protected String mTmdbOrgLang;

    public ZidooTask(@NonNull final Activity activity, @NonNull AudioSubtitleHelper.AudioSubPref prefs, @NonNull BaseItemDto item, @NonNull StreamInfo streamInfo, int taskDelay) {
        super(activity);
        mPrefs = prefs;
        mItem = item;
        mCurrentAudioIdx = null;
        mCurrentSubIdx = null;
        mPlayPos = null;
        mZidooIdentifierHash = null;
        mZidooIdentifierHashStartup = null;
        bestAudioSubIdxZidoo = null;
        mPlayStatus = API_ZIDOO_HTTP_API_VIDEOPLAY_STATUS_ERROR;
        mStreamInfo = streamInfo;
        mPlayMethod = streamInfo.getPlayMethod();
        post(this, taskDelay); // startup
    }

    public void SetTmdbLang(@Nullable TmdbTask tmdbTask) {
        if (tmdbTask != null) {
            mTmdbOrgLang = tmdbTask.getOriginalLanguage(mItem.getId(), mItem.getSeriesId());
        }
    }

    protected boolean updatePlayStatus() {
        mPlayStatus = API_ZIDOO_HTTP_API_VIDEOPLAY_STATUS_ERROR;
        Map<String, Integer> statusMap = getZidooPlayStatusEx();
        Integer status = statusMap.getOrDefault("status", API_ZIDOO_HTTP_API_VIDEOPLAY_STATUS_ERROR);
        if (status != null) {
            mPlayStatus = status;
        }
        if (mPlayStatus >= API_ZIDOO_HTTP_API_VIDEOPLAY_STATUS_PAUSE) {
            // keep old values?
            if (mPlayMethod != PlayMethod.Transcode) {
                mPlayPos = statusMap.getOrDefault("currentPosition", mPlayPos);
            }
            mCurrentAudioIdx = statusMap.getOrDefault("audio_index", mCurrentAudioIdx);
            mCurrentSubIdx = statusMap.getOrDefault("subtitle_index", mCurrentSubIdx);
            mZidooIdentifierHash = statusMap.getOrDefault("id_hash", mZidooIdentifierHash);
        }
        return (mPlayStatus >= API_ZIDOO_HTTP_API_VIDEOPLAY_STATUS_PAUSE);
    }

    protected void setSeekPos(int seekPos) {
        if (seekPos > 0 && mPlayPos != null) { // TODO handle 0 startPos for old API?
            int delta = Math.abs(mPlayPos - seekPos);
            if (delta > API_ZIDOO_SEEKPOS_DELTA) {
                if (setZidooSeekPosition(seekPos)) {
                    Timber.d("setSeekPos success <%s>", getMillisecondsFormated(seekPos));
                } else {
                    Timber.e("setSeekPos failed!");
                }
            } else {
                Timber.d("setSeekPos skipped, within delta <%s><%s>!", delta, API_ZIDOO_SEEKPOS_DELTA);
            }
        }
    }

    // idx in zidoo offsets
    protected void setBestTracks() {
        if (bestAudioSubIdxZidoo == null && mPrefs.mAudioLangSetting != LanguagesAudio.DEVICE) {
            // delay until needed
            Pair<Integer, Integer> audioSubIdx = convertToZidooIndex(getBestAudioSubtitleIdx(mStreamInfo.getMediaSource().getMediaStreams(), mPrefs, mTmdbOrgLang));
            if (mStreamInfo.getPlayMethod() == PlayMethod.Transcode) {
                int subIdx = audioSubIdx.second > 0 ? 1 : 0; // we have only one sub here
                bestAudioSubIdxZidoo = new Pair<>(0, subIdx);
            } else {
                bestAudioSubIdxZidoo = audioSubIdx;
            }
        }
        if (bestAudioSubIdxZidoo != null) {
            // handle audio/sub tracks
            if (bestAudioSubIdxZidoo.first != null && mCurrentAudioIdx != null && !bestAudioSubIdxZidoo.first.equals(mCurrentAudioIdx)) {
                if (setZidooAudioTrack(this.bestAudioSubIdxZidoo.first)) {
                    Timber.d("setZidooAudioTrack success <%s>", bestAudioSubIdxZidoo.first);
                } else {
                    Timber.e("setZidooAudioTrack failed!");
                }
            }
            if (this.bestAudioSubIdxZidoo.second != null && mCurrentSubIdx != null && !bestAudioSubIdxZidoo.second.equals(mCurrentSubIdx)) {
                if (setZidooSubtitleTrack(bestAudioSubIdxZidoo.second)) {
                    Timber.d("setZidooSubtitleTrack success <%s>", bestAudioSubIdxZidoo.second);
                } else {
                    Timber.e("setZidooSubtitleTrack failed!");
                }
            }
        }
    }

    @NonNull
    public static Pair<Integer, Integer> convertToZidooIndex(@Nullable Integer audioIdx, @Nullable Integer subIdx) {
        int audioIdx_out = 0; // "first" track
        int subIdx_out = 0; // no subs
        if (audioIdx != null && audioIdx >= 0) {
            audioIdx_out = audioIdx; // nothing needed starts at base 0
        }
        if (subIdx != null) {
            if (subIdx == SUBTITLE_DISABLED) {
                subIdx_out = 0; // disable subs
            } else {
                subIdx_out = subIdx + 1; // index starts at 1, so offset
            }
        }
        return new Pair<>(audioIdx_out, subIdx_out);
    }

    @NonNull
    public static Pair<Integer, Integer> convertToZidooIndex(@Nullable Pair<Pair<MediaStream, Integer>, Pair<MediaStream, Integer>> audioSubMediaIndex) {
        if (audioSubMediaIndex == null) {
            return new Pair<>(0, 0);
        }
        Pair<MediaStream, Integer> audioPair = audioSubMediaIndex.first;
        Pair<MediaStream, Integer> subPair = audioSubMediaIndex.second;

        Integer audioIdx = null;
        Integer subIdx = null;
        if (audioPair != null && audioPair.second != null) {
            audioIdx = audioPair.second;
        }
        if (subPair != null && subPair.second != null) {
            subIdx = subPair.second;
        }
        return convertToZidooIndex(audioIdx, subIdx);
    }

    // this is in none Zidoo format!
    @NonNull
    public Pair<Integer, Integer> getLastReportedAudioSubIndex() {
        Integer subIdx = null;
        Integer audioIdx = null;
        if (mCurrentSubIdx != null && mCurrentSubIdx >= 0) {
            if (mCurrentSubIdx == 0) {
                subIdx = SUBTITLE_DISABLED;
            } else {
                subIdx = mCurrentSubIdx - 1;
            }
        }
        if (mCurrentAudioIdx != null && mCurrentAudioIdx >= 0) {
            audioIdx = mCurrentAudioIdx;
        }
        return new Pair<>(audioIdx, subIdx);
    }
}

class ZidooStartupTask extends ZidooTask {
    final public int mSeekPos;
    public boolean mZidooStartupOK;
    final private Runnable mCallback;

    public ZidooStartupTask(@NonNull final Activity activity, @NonNull final AudioSubtitleHelper.AudioSubPref prefs, @NonNull BaseItemDto item, int seekPos, @NonNull StreamInfo streamInfo, int taskDelay, @NonNull final Runnable callback) {
        super(activity, prefs, item, streamInfo, taskDelay);
        mSeekPos = seekPos;
        mZidooStartupOK = false;
        mCallback = callback;
    }

    @Override
    protected void finishTask(boolean isReset) {
        if (!mIsFinished) {
            super.finishTask(isReset);
            if (mZidooStartupOK) {
                mActivity.runOnUiThread(mCallback); // make sure its called on Ui thread
            }
        }
    }

    @Override
    public void run() {
        if (mIsFinished) {
            Timber.d("zidooStartupTask is finished");
            return;
        }
        if (!mZidooStartupOK) {
            if (updatePlayStatus()) {
                mZidooStartupOK = true;
                mZidooIdentifierHashStartup = mZidooIdentifierHash;
                Timber.d("zidooStartupTask: pos <%s> audio <%s> sub <%s>", mPlayPos, mCurrentAudioIdx, mCurrentSubIdx);
                setSeekPos(mSeekPos); // only try set once
            }
        }
        // let report task handle audio/subtitles! So we don't overwhelm the player
        long activityPlayTime = System.currentTimeMillis() - mActivityStartTime;
        if (mZidooStartupOK) {
            Timber.d("zidooStartupTask detected ZidooPlayer running!");
            this.finishTask(false);
        } else if (activityPlayTime < API_ZIDOO_STARTUP_TIMEOUT) {
            Timber.d("zidooStartupTask testing failed, try again in %s ms", API_ZIDOO_STARTUP_RETRY_INTERVAL);
            post(this, API_ZIDOO_STARTUP_RETRY_INTERVAL); // try again
        } else {
            Timber.e("zidooStartupTask timeout reached, giving-up!");
            this.finishTask(false);
            mActivity.finish();
        }
    }
}

class ZidooReportTask extends ZidooTask {
    private int mZidooReportTaskErrorCount;
    public Pair<Integer, Integer> mInitialAudioSubIdx;
    public Pair<Integer, Integer> mFinishedAudioSubIdx;
    private boolean started;
    final public int mSeekPos;
    // true when the Zidoo player reported that the video played to the end (end_by=playback_completion)
    public boolean mPlaybackCompleted = false;

    // Auto-skip of Jellyfin media segments (intro/credits)
    static final int SEGMENT_MIN_POLL_INTERVAL = 500; // fastest status poll while waiting for a segment start
    static final int SEGMENT_MIN_REMAINING = 2000; // don't bother skipping when less than this is left of a segment
    static final int SEGMENT_END_MARGIN = 1000; // when credits run to the end, seek to just before the end
    private List<MediaSegmentFetcher.Segment> mSegments = null;
    private final Set<MediaSegmentFetcher.Segment> mHandledSegments = new HashSet<>();

    public ZidooReportTask(@NonNull ZidooTask startupTask, int seekPos, int taskDelay) {
        super(startupTask.mActivity, startupTask.mPrefs, startupTask.mItem, startupTask.mStreamInfo, taskDelay);
        mZidooIdentifierHashStartup = startupTask.mZidooIdentifierHashStartup;
        mZidooReportTaskErrorCount = 0;
        mInitialAudioSubIdx = null;
        mFinishedAudioSubIdx = null;
        started = false;
        mSeekPos = seekPos;
    }

    @Override
    public void run() {
        if (mIsFinished) {
            return;
        }
        if (updatePlayStatus()) {
            if (mZidooIdentifierHashStartup != null && !mZidooIdentifierHashStartup.equals(mZidooIdentifierHash)) {
                Timber.e("ZidooReportTask wrong id_hash <%s> expected <%s>", mZidooIdentifierHash, mZidooIdentifierHashStartup);
                this.finishTask(false);
                mActivity.finish();
                return;
            }
        }
        if (started) {
            if (mPlayStatus == API_ZIDOO_HTTP_API_VIDEOPLAY_STATUS_PLAYING) {
                if (mInitialAudioSubIdx == null) {
                    mInitialAudioSubIdx = new Pair<>(mCurrentAudioIdx, mCurrentSubIdx); // first set after API_ZIDOO_HTTP_API_REPORT_LOOP_INTERVAL ??
                    Timber.d("ZidooReportTask Initial audioIdx: <%s> subTitleIdx: <%s>", mCurrentAudioIdx, mCurrentSubIdx);
                }
                checkSegments();
                if (mPlayMethod != PlayMethod.Transcode && mPlayPos != null && mPlayPos > 0) {
                    ReportingHelper.reportProgress(null, mItem, mStreamInfo, (long) mPlayPos * RUNTIME_TICKS_TO_MS, false);
                    Timber.d("ZidooReportTask reportProgress Status: <%s> Position: <%s> audioIdx: <%s> subIdx: <%s>", mPlayStatus, getMillisecondsFormated(mPlayPos), mCurrentAudioIdx, mCurrentSubIdx);
                }
            }
        } else {
            if (mPlayStatus >= API_ZIDOO_HTTP_API_VIDEOPLAY_STATUS_PAUSE) {
                started = true;

                setBestTracks(); // set audio/video
                loadSegments();

                if (mPlayPos == null) {
                    ReportingHelper.reportStart(mItem, null);
                } else {
                    ReportingHelper.reportStart(mItem, (long) mPlayPos * RUNTIME_TICKS_TO_MS);
                    Timber.d("ZidooReportTask reportStart Status: <%s> Position: <%s>", mPlayStatus, getMillisecondsFormated(mPlayPos));
                }
                // NOTE: We need to report the stream at least so the server see's the transcode infos!
                if (mPlayMethod == PlayMethod.Transcode) {
                    ReportingHelper.reportProgress(null, mItem, mStreamInfo, (long) mSeekPos * RUNTIME_TICKS_TO_MS, false);
                }
                // NOTE: quick first report, so streams get set correctly and we get initial Audio/Sub index
                post(this, 4000);
                return;
            }
        }
        if (mPlayStatus == API_ZIDOO_HTTP_API_VIDEOPLAY_STATUS_ERROR) {
            mZidooReportTaskErrorCount++;
            if (mZidooReportTaskErrorCount > API_ZIDOO_HTTP_API_MAX_ERROR_COUNT) { // ended/error, allow for some hiccups since its a http api
                Timber.e("ZidooReportTask detected invalid Zidoo player status, ending Activity!");
                this.finishTask(false);
                mActivity.finish();
            } else {
                post(this, 1000); // try again in a second
                Timber.d("ZidooReportTask detected Zidoo player http-api status error, trying again in 1000 ms.");
            }
        } else {
            post(this, nextReportDelay());
            mZidooReportTaskErrorCount = 0; // reset
        }
    }

    private void loadSegments() {
        // Transcoded streams report positions relative to the transcode start, so seeking by segment time is unreliable
        if (mPlayMethod == PlayMethod.Transcode) return;
        SegmentSkipBehavior behavior = KoinJavaComponent.<UserPreferences>get(UserPreferences.class).get(UserPreferences.Companion.getSegmentSkipBehavior());
        List<String> types = MediaSegmentFetcher.typesFor(behavior);
        if (types.isEmpty()) return;
        mSegments = MediaSegmentFetcher.fetchBlocking(mItem.getId(), types);
        Timber.i("ZidooReportTask loaded <%d> media segments %s", mSegments.size(), mSegments);
    }

    private void checkSegments() {
        if (mSegments == null || mSegments.isEmpty() || mPlayPos == null) return;
        for (MediaSegmentFetcher.Segment segment : mSegments) {
            if (mHandledSegments.contains(segment)) continue;
            if (mPlayPos >= segment.endMs) {
                // already past it (resumed later or seeked) -> never skip it afterwards
                mHandledSegments.add(segment);
            } else if (mPlayPos >= segment.startMs) {
                mHandledSegments.add(segment);
                if (segment.endMs - mPlayPos < SEGMENT_MIN_REMAINING) continue;

                int target = segment.endMs;
                Long runtimeTicks = mStreamInfo.getRunTimeTicks() != null ? mStreamInfo.getRunTimeTicks() : mItem.getRunTimeTicks();
                if (runtimeTicks != null && runtimeTicks > 0) {
                    int runtimeMs = (int) (runtimeTicks / RUNTIME_TICKS_TO_MS);
                    if (target > runtimeMs - SEGMENT_END_MARGIN) target = runtimeMs - SEGMENT_END_MARGIN;
                }
                if (target <= mPlayPos) continue;

                if (setZidooSeekPosition(target)) {
                    Timber.i("ZidooReportTask skipped segment <%s> from <%s> to <%s>", segment, getMillisecondsFormated(mPlayPos), getMillisecondsFormated(target));
                    mPlayPos = target;
                    final int message;
                    switch (segment.type) {
                        case "Outro": message = R.string.msg_segment_skipped_credits; break;
                        case "Recap": message = R.string.msg_segment_skipped_recap; break;
                        case "Preview": message = R.string.msg_segment_skipped_preview; break;
                        default: message = R.string.msg_segment_skipped_intro; break;
                    }
                    mActivity.runOnUiThread(() -> Toast.makeText(mActivity.getApplicationContext(), message, Toast.LENGTH_SHORT).show());
                } else {
                    Timber.w("ZidooReportTask failed to skip segment <%s>", segment);
                }
                return;
            }
        }
    }

    // Poll sooner when a segment that should be skipped starts before the next regular report tick
    private int nextReportDelay() {
        int delay = API_ZIDOO_HTTP_API_REPORT_LOOP_INTERVAL;
        if (mSegments == null || mPlayPos == null || mPlayStatus != API_ZIDOO_HTTP_API_VIDEOPLAY_STATUS_PLAYING) return delay;
        for (MediaSegmentFetcher.Segment segment : mSegments) {
            if (mHandledSegments.contains(segment)) continue;
            int untilStart = segment.startMs - mPlayPos;
            if (untilStart < delay) delay = Math.max(SEGMENT_MIN_POLL_INTERVAL, untilStart);
        }
        return delay;
    }

    private void updateFromResult(@NonNull Intent data) {
        String endBy = data.getStringExtra("end_by");
//        String url = data.getStringExtra("url");
        int duration = data.getIntExtra("duration", -1);
        int position = data.getIntExtra("position", -1);
        // The Zidoo player uses the MX Player result API: when a video plays to the end it sets
        // end_by=playback_completion and often omits the position. The last polled position can be
        // up to one report interval old, so report the full runtime to make the server mark it watched.
        if ("playback_completion".equals(endBy)) {
            mPlaybackCompleted = true;
            Long runtimeTicks = mStreamInfo.getRunTimeTicks() != null ? mStreamInfo.getRunTimeTicks() : mItem.getRunTimeTicks();
            if (duration > 0) {
                position = duration;
            } else if (runtimeTicks != null && runtimeTicks > 0) {
                position = (int) (runtimeTicks / RUNTIME_TICKS_TO_MS);
            }
            Timber.i("ZidooReportTask detected playback completion, end position <%s>", getMillisecondsFormated(position));
        }
        int audio_idx = data.getIntExtra("audio_idx", -1);
        int subtitle_idx = data.getIntExtra("subtitle_idx", -1);
        // update values from result
        if (position > 0)
            mPlayPos = position;
        if (audio_idx >= 0)
            mCurrentAudioIdx = audio_idx;
        if (subtitle_idx >= 0)
            mCurrentSubIdx = subtitle_idx;
    }

    @Override
    protected void finishTask(boolean isReset) {
        if (!mIsFinished) {
            super.finishTask(isReset);
            if ((mPlayMethod != PlayMethod.Transcode || mPlaybackCompleted) && mPlayPos != null && mPlayPos > 0) {
                ReportingHelper.reportStopped(mItem, mStreamInfo, mPlayPos * RUNTIME_TICKS_TO_MS);
                Timber.d("ZidooReportTask reportStopped Position: <%s>", getMillisecondsFormated(mPlayPos));
            } else {
                long activityPlayTime = System.currentTimeMillis() - mActivityStartTime; // FALLBACK: use activityTime, since seek/playtime is broken
                ReportingHelper.reportStopped(mItem, mStreamInfo, activityPlayTime * RUNTIME_TICKS_TO_MS);
                Timber.d("ZidooReportTask reportStopped fallback Position: <%s>", getMillisecondsFormated((int) activityPlayTime));
            }
            mFinishedAudioSubIdx = new Pair<>(mCurrentAudioIdx, mCurrentSubIdx);
            if (mInitialAudioSubIdx != null) {
                Timber.d("ZidooReportTask Stopped audioIdx: <#%s><%s> subTitleIdx: <#%s><%s>", mCurrentAudioIdx, mInitialAudioSubIdx.first, mCurrentSubIdx, mInitialAudioSubIdx.second);
            }
        }
    }

    public void stop(@Nullable Intent data) {
        if (data != null) {
            updateFromResult(data);
        }
        finishTask(false);
    }
}