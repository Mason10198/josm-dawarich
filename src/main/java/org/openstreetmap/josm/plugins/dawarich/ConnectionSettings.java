package org.openstreetmap.josm.plugins.dawarich;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

public final class ConnectionSettings {
    private final URI base;
    private ConnectionSettings(URI base) { this.base = base; }

    public static ConnectionSettings parse(String value, boolean allowHttp) {
        try {
            URI u = new URI(Objects.requireNonNull(value));
            if (value.matches(".*:[0-9]+(?:/.*)?$") && u.getPort() == -1) throw new IllegalArgumentException("Invalid connection URL");
            String scheme = u.getScheme();
            int port = u.getPort();
            if (u.isOpaque() || u.getHost() == null || u.getHost().isEmpty()
                    || u.getRawUserInfo() != null || u.getRawQuery() != null || u.getRawFragment() != null
                    || !("https".equalsIgnoreCase(scheme) || ("http".equalsIgnoreCase(scheme) && allowHttp))
                    || port < -1 || port > 65535) throw new IllegalArgumentException("Invalid connection URL");
            String rawPath = u.getRawPath() == null ? "" : u.getRawPath();
            while (!rawPath.isEmpty() && rawPath.endsWith("/")) rawPath = rawPath.substring(0, rawPath.length() - 1);
            return new ConnectionSettings(new URI(scheme.toLowerCase(Locale.ROOT) + "://" + u.getRawAuthority() + rawPath));
        } catch (URISyntaxException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid connection URL");
        }
    }
    public URI pointsUri() {
        try { return new URI(base.toASCIIString() + "/api/v1/points"); }
        catch (URISyntaxException e) { throw new IllegalStateException("Invalid endpoint"); }
    }
    public String baseUrl() { return base.toASCIIString(); }
}
