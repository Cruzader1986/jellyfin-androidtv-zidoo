package org.jellyfin.androidtv.auth.apiclient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The legacy apiclient (v0.7.x) calls user scoped routes like {@code /Users/{userId}/Items}. Jellyfin 10.9 added
 * replacements ({@code /Items?userId=}) and marked the old routes "Obsolete - kept for backwards compatibility", the
 * same way legacy authorization was deprecated before it got disabled. When the server is new enough, requests are
 * rewritten to the current routes; older servers keep using the old ones.
 */
public final class LegacyRouteRewriter {
	private static volatile boolean enabled = false;

	// [prefix up to and incl. "/"] Users/{userId}/{rest}[?query]
	private static final Pattern USER_ROUTE = Pattern.compile("^(.*?/)Users/([0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12})/([^?#]*)(\\?[^#]*)?$");

	private static final String[][] RULES = {
		// old route (after Users/{userId}/), new route; {id} is the item id
		{"Items", "Items"},
		{"Items/Resume", "UserItems/Resume"},
		{"Items/Latest", "Items/Latest"},
		{"Items/Root", "Items/Root"},
		{"Items/{id}/Intros", "Items/{id}/Intros"},
		{"Items/{id}/LocalTrailers", "Items/{id}/LocalTrailers"},
		{"Items/{id}/SpecialFeatures", "Items/{id}/SpecialFeatures"},
		{"Items/{id}/Rating", "UserItems/{id}/Rating"},
		{"Items/{id}/UserData", "UserItems/{id}/UserData"},
		{"Items/{id}", "Items/{id}"},
		{"Views", "UserViews"},
		{"GroupingOptions", "UserViews/GroupingOptions"},
		{"PlayedItems/{id}", "UserPlayedItems/{id}"},
		{"FavoriteItems/{id}", "UserFavoriteItems/{id}"},
		{"PlayingItems/{id}/Progress", "PlayingItems/{id}/Progress"},
		{"PlayingItems/{id}", "PlayingItems/{id}"},
	};

	private LegacyRouteRewriter() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	/**
	 * Enable the rewrite when the server version is 10.9.0 or newer (the new routes exist since 10.9).
	 */
	public static void updateServerVersion(String version) {
		enabled = supportsNewRoutes(version);
	}

	static boolean supportsNewRoutes(String version) {
		if (version == null) return false;
		String[] parts = version.trim().split("\\.");
		if (parts.length < 2) return false;
		try {
			int major = Integer.parseInt(parts[0]);
			int minor = Integer.parseInt(parts[1]);
			return major > 10 || (major == 10 && minor >= 9);
		} catch (NumberFormatException e) {
			return false;
		}
	}

	public static String rewrite(String url) {
		if (!enabled || url == null) return url;
		return rewriteAlways(url);
	}

	static String rewriteAlways(String url) {
		Matcher matcher = USER_ROUTE.matcher(url);
		if (!matcher.matches()) return url;

		String prefix = matcher.group(1);
		String userId = matcher.group(2);
		String rest = matcher.group(3);
		String query = matcher.group(4);
		String[] restParts = rest.split("/", -1);

		for (String[] rule : RULES) {
			String[] ruleParts = rule[0].split("/");
			if (ruleParts.length != restParts.length) continue;

			String itemId = null;
			boolean match = true;
			for (int i = 0; i < ruleParts.length; i++) {
				if (ruleParts[i].equals("{id}")) {
					if (restParts[i].isEmpty()) { match = false; break; }
					itemId = restParts[i];
				} else if (!ruleParts[i].equalsIgnoreCase(restParts[i])) {
					match = false;
					break;
				}
			}
			if (!match) continue;

			String newPath = itemId != null ? rule[1].replace("{id}", itemId) : rule[1];
			String newQuery;
			if (query == null || query.length() <= 1) newQuery = "?userId=" + userId;
			else if (query.toLowerCase(java.util.Locale.ROOT).matches(".*[?&]userid=.*")) newQuery = query;
			else newQuery = query + "&userId=" + userId;
			return prefix + newPath + newQuery;
		}
		return url;
	}
}
