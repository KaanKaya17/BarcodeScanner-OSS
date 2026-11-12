package com.example.barcodescanner;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class CatalogSettings {
    static final class Config {
        final String baseUrl;
        final String region;

        Config(String baseUrl, String region) {
            this.baseUrl = baseUrl;
            this.region = region;
        }

        boolean usesCatalog() {
            return !baseUrl.isEmpty();
        }
    }

    private static final String URL = "catalog_url";
    private static final String REGION = "catalog_region";
    private final SharedPreferences storage;

    CatalogSettings(Context context) {
        storage = context.getSharedPreferences("scan_history", Context.MODE_PRIVATE);
    }

    Config read() {
        return new Config(storage.getString(URL, BuildConfig.CATALOG_BASE_URL),
                storage.getString(REGION, BuildConfig.CATALOG_REGION));
    }

    void save(String url, String region) {
        storage.edit().putString(URL, url).putString(REGION, region).apply();
    }

    void restoreDefaults() {
        storage.edit().remove(URL).remove(REGION).apply();
    }

    static String normalizeUrl(String value, boolean allowLocalHttp) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return "";
        try {
            URI uri = new URI(trimmed);
            String host = uri.getHost();
            boolean localHttp = allowLocalHttp && "http".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                    || "10.0.2.2".equals(host));
            if ((!localHttp && !"https".equalsIgnoreCase(uri.getScheme())) || host == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                return null;
            }
            String normalized = uri.normalize().toASCIIString();
            return normalized.endsWith("/") ? normalized : normalized + "/";
        } catch (URISyntaxException e) {
            return null;
        }
    }

    static String normalizeRegion(String value) {
        String region = value.trim().toUpperCase(Locale.ROOT);
        return region.matches("[A-Z]{2}") ? region : null;
    }
}
