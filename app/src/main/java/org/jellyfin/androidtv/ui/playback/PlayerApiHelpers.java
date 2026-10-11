package org.jellyfin.androidtv.ui.playback;

import static org.jellyfin.androidtv.util.Utils.isEmptyTrim;
import static org.jellyfin.androidtv.util.Utils.isNonEmpty;
import static org.jellyfin.androidtv.util.Utils.isNonEmptyTrim;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import org.jetbrains.annotations.Contract;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

public class PlayerApiHelpers {
    private static HttpURLConnection mHttpConnZidooApi;
    private static HttpURLConnection mHttpConnTmdbApi;

    final public static int SUBTITLE_DISABLED = -1;
    final public static int INVALID_TRACK_NR = -99;

    // http://apidoc.zidoo.tv/s/98365225/Gmwqxawu/BI0Cv1r2
    static final String API_ZIDOO_HTTP_API_IP = "127.0.0.1:9529";
    static final String API_ZIDOO_HTTP_API_TARGET_VIDEOPLAY = "ZidooVideoPlay";
    static final String API_ZIDOO_HTTP_API_JSON_STATUS = "status";
    static final int API_ZIDOO_HTTP_API_VIDEOPLAY_STATUS_ERROR = -1;
    static final int API_ZIDOO_HTTP_API_VIDEOPLAY_STATUS_PLAYING = 1;
    static final int API_ZIDOO_HTTP_API_VIDEOPLAY_STATUS_PAUSE = 0;
    static final int API_ZIDOO_HTTP_API_SUCCESS = 200;
    static final int API_ZIDOO_HTTP_API_NORESOURCE = 806;
    // https://developers.themoviedb.org/3/getting-started/introduction
    static final String API_TMDB_HTTP_API_KEY = "4219e299c89411838049ab0dab19ebd5"; // from Jellyfin server
    static final String API_TMDB_HTTP_API_V3_BASE_URL = "api.themoviedb.org/3";
    static final String API_TMDB_HTTP_API_JSON_ID = "id";

    // https://api.themoviedb.org/3/tv/{tv_id}?api_key=<<api_key>>&language=en-US
    @Nullable
    public static JSONObject getFromTmdbHttp_API(@NonNull String url_api_target, String url_cmd, String url_parameter, String json_objId) {
        if (isEmptyTrim(url_api_target) || isEmptyTrim(url_cmd)) {
            Timber.w("getFromTmdbHttp_API failed, empty input parameters.");
            return null;
        }

        BufferedReader reader;
        String line;
        StringBuilder responseContent = new StringBuilder();

        try {
            String url_string = "https://" + API_TMDB_HTTP_API_V3_BASE_URL + "/" + url_api_target + "/" + url_cmd;
            url_string += "?api_key=" + API_TMDB_HTTP_API_KEY + "&language=en-US";
            if (isNonEmptyTrim(url_parameter)) {
                url_string += "&" + url_parameter;
            }
            URL url = new URL(url_string);

            mHttpConnTmdbApi = (HttpURLConnection) url.openConnection();
            // Request setup
            mHttpConnTmdbApi.setRequestMethod("GET");
            mHttpConnTmdbApi.setConnectTimeout(3000);
            mHttpConnTmdbApi.setReadTimeout(3000);

            // Test if the response from the server is successful
            int http_status = mHttpConnTmdbApi.getResponseCode();
            if (http_status == 200) {
                reader = new BufferedReader(new InputStreamReader(mHttpConnTmdbApi.getInputStream()));
                while ((line = reader.readLine()) != null) {
                    responseContent.append(line);
                }
                reader.close();
                // try parse json, only return if valid status
                JSONObject tmdb_obj = new JSONObject(responseContent.toString());
                if (tmdb_obj.optInt(API_TMDB_HTTP_API_JSON_ID) > 0 || tmdb_obj.has("tv_results")) { // check for tmdbid
                    if (isNonEmptyTrim(json_objId)) {
                        return tmdb_obj.getJSONObject(json_objId);
                    }
                    return tmdb_obj;
                }
            } else {
                Timber.d("getFromTmdbHttp_API status error <%s>", http_status);
            }
        } catch (JSONException | IOException e) {
            Timber.d("getFromTmdbHttp_API failed, could not reach target or status error.");
        } finally {
            if (mHttpConnTmdbApi != null) {
                mHttpConnTmdbApi.disconnect();
                mHttpConnTmdbApi = null;
            }
        }

        return null;
    }

    @Nullable
    public static JSONObject getFromZidooHTTP_API(@NonNull String url_api_target, String url_cmd, String url_parameter, String json_objId) {
        if (isEmptyTrim(url_api_target) || isEmptyTrim(url_cmd)) {
            Timber.e("getFromZidooHTTP_API failed, empty input parameters.");
            return null;
        }

        BufferedReader reader;
        String line;
        StringBuilder responseContent = new StringBuilder();

        try {
            String url_string = "http://" + API_ZIDOO_HTTP_API_IP + "/" + url_api_target + "/" + url_cmd;
            if (isNonEmptyTrim(url_parameter)) {
                url_string += "?" + url_parameter;
            }
            URL url = new URL(url_string);

            mHttpConnZidooApi = (HttpURLConnection) url.openConnection();
            // Request setup
            mHttpConnZidooApi.setRequestMethod("GET");
            mHttpConnZidooApi.setConnectTimeout(2000);
            mHttpConnZidooApi.setReadTimeout(2000);

            // Test if the response from the server is successful
            int http_status = mHttpConnZidooApi.getResponseCode();
            if (http_status == 200) {
                reader = new BufferedReader(new InputStreamReader(mHttpConnZidooApi.getInputStream()));
                while ((line = reader.readLine()) != null) {
                    responseContent.append(line);
                }
                reader.close();
                // try parse json, only return if valid status
                JSONObject zidoo_obj = new JSONObject(responseContent.toString());
                if (zidoo_obj.optInt(API_ZIDOO_HTTP_API_JSON_STATUS) == API_ZIDOO_HTTP_API_SUCCESS) {
                    if (isNonEmptyTrim(json_objId)) {
                        zidoo_obj = zidoo_obj.getJSONObject(json_objId);
                    }
                    return zidoo_obj;
                }
            }
        } catch (JSONException | IOException e) {
            Timber.d("getFromZidooHTTP_API failed, could not reach target or status error.");
        } finally {
            if (mHttpConnZidooApi != null) {
                mHttpConnZidooApi.disconnect();
                mHttpConnZidooApi = null;
            }
        }

        return null;
    }

    //GET/VideoPlay/setAudio?index=0        NOTE: Audio index start at 0
    public static boolean setZidooAudioTrack(int idx) {
        JSONObject zidoo_obj = getFromZidooHTTP_API(API_ZIDOO_HTTP_API_TARGET_VIDEOPLAY, "setAudio", "index=" + idx, "");
        return zidoo_obj != null;
    }

    //GET/VideoPlay/setSubtitle?index=1     NOTE: index=0 turns off, so Subtitle index start at 1 !!!
    public static boolean setZidooSubtitleTrack(int idx) {
        JSONObject zidoo_obj = getFromZidooHTTP_API(API_ZIDOO_HTTP_API_TARGET_VIDEOPLAY, "setSubtitle", "index=" + idx, "");
        return zidoo_obj != null;
    }

    //GET/VideoPlay/seekTo?positon=300000
    public static boolean setZidooSeekPosition(int seekPos) {
        JSONObject zidoo_obj = getFromZidooHTTP_API(API_ZIDOO_HTTP_API_TARGET_VIDEOPLAY, "seekTo", "positon=" + seekPos, "");
        return zidoo_obj != null;
    }

    //GET/VideoPlay/getPlayStatus
    // returns Video<status, position>
    @NonNull
    @Contract(" -> new")
    public Pair<Integer, Integer> getZidooPlayStatus() {
        JSONObject video_obj = getFromZidooHTTP_API(API_ZIDOO_HTTP_API_TARGET_VIDEOPLAY, "getPlayStatus", "", "video");
        if (video_obj != null) {
            int duration = video_obj.optInt("duration");
            if (duration > 0) { // sanity check if we have valid data
                return new Pair<>(video_obj.optInt("status", API_ZIDOO_HTTP_API_VIDEOPLAY_STATUS_ERROR), video_obj.optInt("currentPosition", -1));
            }
        }
        return new Pair<>(API_ZIDOO_HTTP_API_VIDEOPLAY_STATUS_ERROR, -1);
    }

    @NonNull
    public static Map<String, Integer> getZidooPlayStatusEx() {
        Map<String, Integer> outMap = new HashMap<>();
        JSONObject zidoo_obj = getFromZidooHTTP_API(API_ZIDOO_HTTP_API_TARGET_VIDEOPLAY, "getPlayStatus", "", "");
        if (zidoo_obj != null) {
            try {
                JSONObject video_obj = zidoo_obj.getJSONObject("video");
                int width = video_obj.optInt("width", -1);
                if (width > 0) { // sanity check if we have valid data
                    if (video_obj.has("status")) {
                        outMap.put("status", video_obj.optInt("status", API_ZIDOO_HTTP_API_VIDEOPLAY_STATUS_ERROR));
                    }
                    int pos = video_obj.optInt("currentPosition", -1);
                    int duration = video_obj.optInt("duration", -1);
                    if (duration > 0 && pos >= 0) {  // carefully, don't send pos for "broken" HLS streams!
                        outMap.put("currentPosition", pos);
                    }
                    String hashId = video_obj.optString("path", "");
                    if (isEmptyTrim(hashId)) {
                        hashId = video_obj.optString("title", "");
                    }
                    if (isNonEmptyTrim(hashId)) {
                        outMap.put("id_hash", hashId.hashCode());
                    }
                    if (zidoo_obj.has("audio")) {
                        JSONObject audio_obj = zidoo_obj.getJSONObject("audio");
                        if (audio_obj.has("index")) {
                            outMap.put("audio_index", audio_obj.optInt("index", INVALID_TRACK_NR));
                        }
                    }
                    if (zidoo_obj.has("subtitle")) {
                        JSONObject subtitle_obj = zidoo_obj.getJSONObject("subtitle");
                        if (subtitle_obj.has("index")) {
                            outMap.put("subtitle_index", subtitle_obj.optInt("index", INVALID_TRACK_NR));
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return outMap;
    }

    @NonNull
    public static Pair<String,String> getSmbUserPass(@NonNull Uri pathUri) {
        String user = null;
        String pass = null;
        String userInfo = pathUri.getUserInfo();
        if (isNonEmptyTrim(userInfo)) {
            String[] splitArray = userInfo.split(":", 2);
            if (splitArray.length >= 1) {
                String smb_username = splitArray[0].trim();
                if (isNonEmptyTrim(smb_username)) {
                    user = smb_username;
                }
                if (splitArray.length >= 2) {
                    String smb_password = splitArray[1].trim();
                    if (isNonEmptyTrim(smb_password)) {
                        pass = smb_password;
                    }
                }
            }
        }
        if (isEmptyTrim(user)) {
            user = "guest";
        }
        return new Pair<>(user, pass);
    }

    // root_path, share_path
    @NonNull
    public static Pair<String,String> getNfsRoot(@NonNull Uri pathUri) {
        String nfs_root = null;
        String nfs_share = null;
        if (isNonEmpty(pathUri.getPathSegments())) {
            java.util.List<String> segments = pathUri.getPathSegments();
            // Guess the NFS export root when no "/:" marker is given (Andy2244/jellyfin-androidtv-zidoo#11).
            // Mounting a sub directory of an export generally works, mounting a parent of it never does,
            // so prefer the deeper guess for well known NAS layouts.
            int depth = guessNfsExportDepth(segments);
            nfs_share = segments.get(depth - 1);
            StringBuilder root = new StringBuilder(pathUri.getHost());
            for (int i = 0; i < depth; i++) root.append('/').append(segments.get(i));
            nfs_root = root.toString();
        }
        String[] splitArray = pathUri.getPath().split("/:", 2); // we use "/:" as marker for the export path
        if (splitArray.length > 1) {
//            nfs_share = splitArray[0];
//            if (!nfs_share.isEmpty() && nfs_share.charAt(0) == '/') {
//                nfs_share = nfs_share.replaceFirst("/",""); // we need just the sharename
//            }
            nfs_root = pathUri.getHost() + splitArray[0];
        }
        return new Pair<>(nfs_root, nfs_share);
    }

    /**
     * Number of leading path segments that make up the NFS export, e.g.
     * Unraid /mnt/user/media (3), TrueNAS /mnt/pool/dataset (3), Synology /volume1/video (2), /export/media (2),
     * otherwise the first segment (1). Always leaves at least the file name outside the export.
     */
    static int guessNfsExportDepth(@NonNull java.util.List<String> segments) {
        int depth = 1;
        String first = segments.get(0).toLowerCase(java.util.Locale.ROOT);
        if (first.equals("mnt") || first.equals("media")) {
            depth = 3;
        } else if (first.matches("volume\\d+") || first.equals("export") || first.equals("exports") || first.equals("srv") || first.equals("share") || first.equals("shares")) {
            depth = 2;
        }
        return Math.max(1, Math.min(depth, segments.size() - 1));
    }

    public static void testNFS(@NonNull Intent initIntent) {
        Uri uri = initIntent.getData();
        String mPath = uri.toString();
        mPath = mPath.replace("nfs://", "");

        String ip = uri.getHost();
        String nfs_root = initIntent.getStringExtra("nfs_root");
        mPath = mPath.replace(nfs_root, "");
        String ip2 = nfs_root.substring(0, nfs_root.indexOf("/"));
        String id = nfs_root.substring(nfs_root.lastIndexOf("/"));

        String outPath = ip2 + id + mPath;
        Timber.d("ZDMCActivity = out mPath = %s", outPath);
    }
}
