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
		final String url = LegacyRouteRewriter.rewrite(request.getUrl());

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
