package org.jellyfin.androidtv.auth.apiclient;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts the authentication headers produced by the legacy Jellyfin apiclient (v0.7.x) into the
 * single {@code Authorization: MediaBrowser ...} header required by current Jellyfin servers.
 * <p>
 * The legacy client sends {@code X-Emby-Authorization: MediaBrowser Client="..", ...} plus the
 * access token in a separate {@code X-MediaBrowser-Token} header. Jellyfin 10.11+ (and 12.x by
 * default) disabled "legacy authorization", so both headers are ignored and every request returns
 * 401. Modern servers only accept the token inside the {@code Authorization} header
 * ({@code Token="..."}) or as the {@code ApiKey} query parameter.
 */
public final class ModernAuthorizationHeader {
	public static final String HEADER_AUTHORIZATION = "Authorization";
	public static final String LEGACY_HEADER_AUTHORIZATION = "X-Emby-Authorization";
	public static final String LEGACY_HEADER_TOKEN = "X-MediaBrowser-Token";
	public static final String LEGACY_HEADER_EMBY_TOKEN = "X-Emby-Token";
	public static final String SCHEME = "MediaBrowser";

	private static final Pattern PARAMETER = Pattern.compile("([A-Za-z]+)\\s*=\\s*\"([^\"]*)\"");

	private ModernAuthorizationHeader() {
	}

	/**
	 * Rewrite a mutable header map in place: legacy auth headers are removed and replaced by a
	 * modern {@code Authorization} header (when there is anything to send).
	 */
	public static void rewrite(Map<String, String> headers) {
		String legacyAuthorization = removeIgnoreCase(headers, LEGACY_HEADER_AUTHORIZATION);
		String token = removeIgnoreCase(headers, LEGACY_HEADER_TOKEN);
		String embyToken = removeIgnoreCase(headers, LEGACY_HEADER_EMBY_TOKEN);
		if (isEmpty(token)) token = embyToken;

		String existing = getIgnoreCase(headers, HEADER_AUTHORIZATION);
		String source = legacyAuthorization != null ? legacyAuthorization : existing;

		String modern = build(source, token);
		if (modern != null) {
			removeIgnoreCase(headers, HEADER_AUTHORIZATION);
			headers.put(HEADER_AUTHORIZATION, modern);
		}
	}

	/**
	 * Build a modern authorization header value.
	 *
	 * @param legacyHeader value of the legacy header, e.g. {@code MediaBrowser Client="x", DeviceId="y"} (may be null)
	 * @param token        access token (may be null)
	 * @return header value or null when there is nothing to send
	 */
	public static String build(String legacyHeader, String token) {
		Map<String, String> params = parse(legacyHeader);
		params.remove("Token");
		if (!isEmpty(token)) params.put("Token", token);
		if (params.isEmpty()) return null;

		StringBuilder builder = new StringBuilder(SCHEME).append(' ');
		boolean first = true;
		for (Map.Entry<String, String> entry : params.entrySet()) {
			if (!first) builder.append(", ");
			first = false;
			builder.append(entry.getKey()).append("=\"").append(encode(entry.getValue())).append('"');
		}
		return builder.toString();
	}

	/**
	 * Parse {@code [Scheme ]Key="Value", Key2="Value2"} into an ordered map. Values are kept raw.
	 */
	static Map<String, String> parse(String header) {
		Map<String, String> result = new LinkedHashMap<>();
		if (isEmpty(header)) return result;

		Matcher matcher = PARAMETER.matcher(header);
		while (matcher.find()) {
			String value = matcher.group(2);
			if (value != null && !value.isEmpty()) result.put(matcher.group(1), value);
		}
		return result;
	}

	/**
	 * Same encoding as the official Kotlin SDK (AuthorizationHeaderBuilder.encodeParameterValue):
	 * trim, newlines to spaces, URL-encode with spaces as '+'. The server URL-decodes every value.
	 */
	static String encode(String raw) {
		String value = raw.trim().replace('\n', ' ');
		try {
			return URLEncoder.encode(value, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			// UTF-8 is always supported
			throw new IllegalStateException(e);
		}
	}

	private static String removeIgnoreCase(Map<String, String> headers, String name) {
		String found = null;
		String foundKey = null;
		for (Map.Entry<String, String> entry : headers.entrySet()) {
			if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
				foundKey = entry.getKey();
				found = entry.getValue();
				break;
			}
		}
		if (foundKey != null) headers.remove(foundKey);
		return found;
	}

	private static String getIgnoreCase(Map<String, String> headers, String name) {
		for (Map.Entry<String, String> entry : headers.entrySet()) {
			if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
		}
		return null;
	}

	private static boolean isEmpty(String value) {
		return value == null || value.isEmpty();
	}
}
