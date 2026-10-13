package org.jellyfin.androidtv.auth.apiclient;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;

import org.jellyfin.apiclient.interaction.VolleyErrorListener;
import org.jellyfin.apiclient.interaction.VolleyHttpClient;
import org.jellyfin.apiclient.interaction.VolleyResponseListener;
import org.jellyfin.apiclient.interaction.VolleyStringRequest;
import org.jellyfin.apiclient.interaction.http.HttpRequest;
import org.jellyfin.apiclient.logging.ILogger;

import java.util.HashMap;
import java.util.Map;

/**
 * Drop-in replacement for the legacy apiclient's {@link VolleyHttpClient} that sends the modern
 * {@code Authorization: MediaBrowser ..., Token="..."} header instead of the legacy
 * {@code X-Emby-Authorization} / {@code X-MediaBrowser-Token} headers, which current Jellyfin
 * servers reject. See {@link ModernAuthorizationHeader}.
 */
public class ModernAuthVolleyHttpClient extends VolleyHttpClient {
	private final ILogger logger;

	public ModernAuthVolleyHttpClient(ILogger logger, Context context) {
		super(logger, context);
		this.logger = logger;
	}

	@Override
	public void Send(final HttpRequest request, final org.jellyfin.apiclient.interaction.Response<String> response) {
		int method = Request.Method.GET;

		if ("POST".equalsIgnoreCase(request.getMethod())) {
			method = Request.Method.POST;
		} else if ("DELETE".equalsIgnoreCase(request.getMethod())) {
			method = Request.Method.DELETE;
		}

		// Use the current (Jellyfin 10.9+) routes instead of the obsolete /Users/{userId}/... ones when supported
		final String url = addRecursiveItemCount(LegacyRouteRewriter.rewrite(request.getUrl()));

		StringRequest req = new ModernAuthStringRequest(method,
				url,
				new VolleyResponseListener<>(response, logger, url),
				new VolleyErrorListener<>(response, logger),
				request);

		if (method != Request.Method.GET) {
			req.setShouldCache(false);
		}

		if (!request.getEnableCaching()) {
			req.setShouldCache(false);
		}

		req.setRetryPolicy(new DefaultRetryPolicy(
				request.getTimeout(),
				0,
				DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
		));

		addToRequestQueue(req);
	}

	private static final java.util.regex.Pattern FIELDS_PARAM = java.util.regex.Pattern.compile("([?&][Ff]ields=)([^&#]*)");

	/**
	 * Ask for RecursiveItemCount whenever fields are requested, so series/season cards can show how
	 * much has been watched (the server only fills UserData.PlayedPercentage with it).
	 */
	static String addRecursiveItemCount(String url) {
		if (url == null) return null;
		java.util.regex.Matcher matcher = FIELDS_PARAM.matcher(url);
		if (!matcher.find()) return url;
		String value = matcher.group(2);
		if (value.toLowerCase(java.util.Locale.ROOT).contains("recursiveitemcount")) return url;
		String separator = value.isEmpty() ? "" : (value.contains(",") ? "," : "%2C");
		return url.substring(0, matcher.end(2)) + separator + "RecursiveItemCount" + url.substring(matcher.end(2));
	}

	static class ModernAuthStringRequest extends VolleyStringRequest {
		ModernAuthStringRequest(int method, String url,
								com.android.volley.Response.Listener<String> listener,
								com.android.volley.Response.ErrorListener errorListener,
								HttpRequest request) {
			super(method, url, listener, errorListener, request);
		}

		@Override
		public Map<String, String> getHeaders() throws AuthFailureError {
			Map<String, String> headers = new HashMap<>(super.getHeaders());
			ModernAuthorizationHeader.rewrite(headers);
			return headers;
		}
	}
}
