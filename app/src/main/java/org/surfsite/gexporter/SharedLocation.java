package org.surfsite.gexporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A single destination shared from a map app (Google Maps, Organic Maps,
 * any app emitting geo: URIs), turned into a one-waypoint GPX file that
 * gimporter saves as a Garmin location.
 */
public class SharedLocation {
    private static final Logger Log = LoggerFactory.getLogger(SharedLocation.class);

    public static final String DEFAULT_NAME = "Location";

    private static final String NUM = "(-?\\d{1,3}(?:\\.\\d+)?)";
    // Free-text coordinates must have decimals, or "Hauptstr. 12, 34" would match.
    private static final String DEC = "(-?\\d{1,3}\\.\\d+)";
    private static final String SEP = "\\s*,\\s*";

    private static final Pattern URL_PATTERN = Pattern.compile("(?:https?|geo|ge0|om):[^\\s<>\"]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern GEO_URI = Pattern.compile("^geo:" + NUM + SEP + NUM + "[^?]*(?:\\?(.*))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GEO_QUERY = Pattern.compile("^" + NUM + SEP + NUM + "\\s*(?:\\((.*)\\))?$");
    // Google place URLs carry the exact place position as !3d<lat>!4d<lon>;
    // the @<lat>,<lon> part is only the map viewport, so it is the fallback.
    private static final Pattern GOOGLE_PLACE = Pattern.compile("!3d" + NUM + "!4d" + NUM);
    private static final Pattern GOOGLE_VIEWPORT = Pattern.compile("@" + NUM + "," + NUM);
    private static final Pattern GOOGLE_PLACE_NAME = Pattern.compile("/maps/place/([^/@?]+)");
    private static final Pattern QUERY_COORDS = Pattern.compile("[?&](?:q|ll|query|destination|daddr|center|sll)=" + NUM + "(?:,|%2C)\\+?" + NUM, Pattern.CASE_INSENSITIVE);
    // In the EU, Google redirects every request without this cookie to the
    // consent dialog, so following a place URL would loop through it.
    static final String GOOGLE_CONSENT_COOKIE = "SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg";
    // Organic Maps links, see organicmaps libs/ge0/parser.cpp: either a ge0
    // code (1 zoom char + 9 lat/lon chars) or clear "<lat>,<lon>", then an
    // optional "/<name>" with '_' and ' ' swapped.
    private static final Pattern OM_URL = Pattern.compile(
            "^(?:https?://(?:omaps\\.app|ge0\\.me)/|om://|ge0://)([^/?#]+)(?:/([^?#]*))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern OM_CLEAR = Pattern.compile("^" + DEC + "," + DEC + "$");
    private static final String GE0_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    private static final int GE0_MAX_POINT_BYTES = 10;
    private static final int GE0_MAX_COORD_BITS = GE0_MAX_POINT_BYTES * 3;
    private static final Pattern PLAIN_COORDS = Pattern.compile("(?<![\\d.])" + DEC + SEP + DEC + "(?![\\d.])");

    private final String name;
    private final double lat;
    private final double lon;

    public SharedLocation(String name, double lat, double lon) {
        this.name = (name == null || name.trim().isEmpty()) ? DEFAULT_NAME : name.trim();
        this.lat = lat;
        this.lon = lon;
    }

    public String getName() {
        return name;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    /** Fetches a URL without following redirects; injectable for tests. */
    public interface Fetcher {
        /** @return the redirect target, or null if the response was not a redirect */
        String redirectTarget(String url) throws IOException;
    }

    /**
     * Parse shared text without network access.
     *
     * @return the location, or null if the text holds no usable coordinates
     */
    public static SharedLocation parse(String text) {
        if (text == null) {
            return null;
        }
        String name = firstNonUrlLine(text);

        Matcher m = URL_PATTERN.matcher(text);
        while (m.find()) {
            SharedLocation loc = parseUrl(m.group(), name);
            if (loc != null) {
                return loc;
            }
        }

        Matcher c = PLAIN_COORDS.matcher(text);
        while (c.find()) {
            SharedLocation loc = fromStrings(name, c.group(1), c.group(2));
            if (loc != null) {
                return loc;
            }
        }
        return null;
    }

    /**
     * Parse shared text, resolving short links (e.g. maps.app.goo.gl) over
     * the network when the text itself holds no coordinates. Must not be
     * called on the UI thread.
     */
    public static SharedLocation resolve(String text, Fetcher fetcher) {
        SharedLocation loc = parse(text);
        if (loc != null || text == null) {
            return loc;
        }
        String name = firstNonUrlLine(text);

        Matcher m = URL_PATTERN.matcher(text);
        while (m.find()) {
            String url = m.group();
            if (!url.toLowerCase(Locale.ROOT).startsWith("http")) {
                continue;
            }
            try {
                loc = followRedirects(url, name, fetcher);
            } catch (IOException e) {
                Log.warn("Could not resolve {}: {}", url, e.toString());
            }
            if (loc != null) {
                return loc;
            }
        }
        return null;
    }

    private static SharedLocation followRedirects(String url, String name, Fetcher fetcher) throws IOException {
        String start = url;
        for (int hops = 0; hops < 10; hops++) {
            String next = fetcher.redirectTarget(url);
            if (next == null) {
                break;
            }
            url = new URL(new URL(url), next).toString();
            Log.info("Redirected to {}", url);
            // EU consent interstitial wraps the real target in ?continue=
            String cont = queryParam(url, "continue");
            if (cont != null) {
                url = cont;
            }
            SharedLocation loc = parseUrl(url, name);
            if (loc != null) {
                return loc;
            }
        }

        // Google place links often resolve to /maps/place/<name>/data=...
        // with only a feature id. The page does not help: the only position
        // in it is the map view around the requester's IP location, not the
        // place (the place data is loaded by script later).
        Log.warn("No coordinates in {} (resolved to {})", start, url);
        return null;
    }

    static SharedLocation parseUrl(String url, String fallbackName) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("geo:")) {
            return parseGeoUri(url, fallbackName);
        }
        if (isGoogle(url)) {
            return parseGoogleUrl(url, fallbackName);
        }
        return parseOrganicMapsUrl(url, fallbackName);
    }

    private static SharedLocation parseOrganicMapsUrl(String url, String fallbackName) {
        Matcher m = OM_URL.matcher(url);
        if (!m.find()) {
            return null;
        }
        String code = m.group(1);
        String name = m.group(2) != null ? decodeOrganicMapsName(m.group(2)) : "";
        if (name.trim().isEmpty()) {
            name = fallbackName;
        }

        Matcher clear = OM_CLEAR.matcher(code);
        if (clear.matches()) {
            return fromStrings(name, clear.group(1), clear.group(2));
        }
        // omaps.app/get etc. are pages, not ge0 codes
        if (code.length() != GE0_MAX_POINT_BYTES) {
            return null;
        }
        return decodeGe0(code.substring(1), name);
    }

    /** Port of Ge0Parser::DecodeLatLon (9 base64 chars, 3 lat + 3 lon bits each). */
    private static SharedLocation decodeGe0(String s, String name) {
        int latI = 0;
        int lonI = 0;
        int shift = GE0_MAX_COORD_BITS - 3;
        for (int i = 0; i < s.length(); i++, shift -= 3) {
            int a = GE0_ALPHABET.indexOf(s.charAt(i));
            if (a < 0) {
                return null;
            }
            int lat1 = ((a >> 5) & 1) << 2 | ((a >> 3) & 1) << 1 | ((a >> 1) & 1);
            int lon1 = ((a >> 4) & 1) << 2 | ((a >> 2) & 1) << 1 | (a & 1);
            latI |= lat1 << shift;
            lonI |= lon1 << shift;
        }
        int remainingBits = 3 * (GE0_MAX_POINT_BYTES - s.length()) - 1;
        if (remainingBits >= 0) {
            latI += 1 << remainingBits;
            lonI += 1 << remainingBits;
        }
        int maxValue = (1 << GE0_MAX_COORD_BITS) - 1;
        double lat = (double) latI / maxValue * 180.0 - 90.0;
        double lon = (double) lonI / (maxValue + 1.0) * 360.0 - 180.0;
        return new SharedLocation(name, lat, lon);
    }

    /** Port of Ge0Parser::DecodeName: drop a broken %-escape, decode, swap '_' and ' '. */
    private static String decodeOrganicMapsName(String name) {
        for (int i = 0; i + 2 < name.length(); i++) {
            if (name.charAt(i) == '%' && (!isHex(name.charAt(i + 1)) || !isHex(name.charAt(i + 2)))) {
                name = name.substring(0, i);
                break;
            }
        }
        if (name.endsWith("%")) {
            name = name.substring(0, name.length() - 1);
        } else if (name.length() > 1 && name.charAt(name.length() - 2) == '%') {
            name = name.substring(0, name.length() - 2);
        }
        // '+' is literal here, not an encoded space
        name = urlDecode(name.replace("+", "%2B"));
        StringBuilder sb = new StringBuilder(name.length());
        for (char c : name.toCharArray()) {
            sb.append(c == '_' ? ' ' : c == ' ' ? '_' : c);
        }
        return sb.toString();
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
    }

    private static SharedLocation parseGeoUri(String uri, String fallbackName) {
        Matcher m = GEO_URI.matcher(uri);
        if (!m.matches()) {
            return null;
        }
        String q = queryParam(uri, "q");
        if (q != null) {
            // geo:0,0?q=lat,lon(Label) is the documented form for a labelled pin
            Matcher qm = GEO_QUERY.matcher(q.trim());
            if (qm.matches()) {
                SharedLocation loc = fromStrings(qm.group(3) != null ? qm.group(3) : fallbackName,
                        qm.group(1), qm.group(2));
                if (loc != null) {
                    return loc;
                }
            }
        }
        SharedLocation loc = fromStrings(fallbackName, m.group(1), m.group(2));
        if (loc != null && loc.lat == 0.0 && loc.lon == 0.0) {
            // geo:0,0?q=<address> needs geocoding, which we do not do
            return null;
        }
        return loc;
    }

    private static SharedLocation parseGoogleUrl(String url, String fallbackName) {
        String placeName = googlePlaceName(url);
        String name = placeName != null ? placeName : fallbackName;

        for (Pattern p : new Pattern[]{GOOGLE_PLACE, QUERY_COORDS, GOOGLE_VIEWPORT}) {
            Matcher m = p.matcher(url);
            if (m.find()) {
                SharedLocation loc = fromStrings(name, m.group(1), m.group(2));
                if (loc != null) {
                    return loc;
                }
            }
        }
        return null;
    }

    private static boolean isGoogle(String url) {
        String host;
        try {
            host = new URL(url).getHost().toLowerCase(Locale.ROOT);
        } catch (IOException e) {
            return false;
        }
        return host.equals("goo.gl") || host.endsWith(".goo.gl")
                || host.matches("(?:.+\\.)?google\\.[a-z.]+");
    }

    private static String googlePlaceName(String url) {
        Matcher m = GOOGLE_PLACE_NAME.matcher(url);
        if (!m.find()) {
            return null;
        }
        String placeName = urlDecode(m.group(1)).trim();
        // A dropped pin is "named" by its own coordinates; that is no name.
        if (placeName.isEmpty() || PLAIN_COORDS.matcher(placeName).find() || placeName.contains("°")) {
            return null;
        }
        // "FRISTO Getränkemarkt, Johann-Sebastian-Bach-Straße 5, 85591 ...":
        // the watch shows short names, so keep the part before the address.
        int comma = placeName.indexOf(',');
        if (comma > 0) {
            placeName = placeName.substring(0, comma).trim();
        }
        return placeName;
    }

    private static String queryParam(String url, String key) {
        int q = url.indexOf('?');
        if (q < 0) {
            return null;
        }
        for (String pair : url.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equalsIgnoreCase(key)) {
                return urlDecode(pair.substring(eq + 1));
            }
        }
        return null;
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return s;
        }
    }

    private static String firstNonUrlLine(String text) {
        for (String line : text.split("\\r?\\n")) {
            String stripped = URL_PATTERN.matcher(line).replaceAll("").trim();
            if (!stripped.isEmpty() && !PLAIN_COORDS.matcher(stripped).matches()) {
                return stripped;
            }
        }
        return null;
    }

    private static SharedLocation fromStrings(String name, String latStr, String lonStr) {
        double lat;
        double lon;
        try {
            lat = Double.parseDouble(latStr);
            lon = Double.parseDouble(lonStr);
        } catch (NumberFormatException e) {
            return null;
        }
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
            return null;
        }
        return new SharedLocation(name, lat, lon);
    }

    /**
     * The ConnectIQ message gimporter acts on. Coordinates are strings so the
     * watch does not round them to 32-bit floats; "time" (epoch seconds) lets
     * it ignore a location still queued from an earlier share.
     */
    public Map<String, Object> toWatchMessage(long nowMillis) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "location");
        msg.put("title", name);
        msg.put("lat", String.format(Locale.ROOT, "%.7f", lat));
        msg.put("lon", String.format(Locale.ROOT, "%.7f", lon));
        msg.put("time", (int) (nowMillis / 1000));
        return msg;
    }

    /** A GPX 1.1 document holding just this location as a single waypoint. */
    public String toGpx() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<gpx version=\"1.1\" creator=\"gexporter\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n"
                + String.format(Locale.ROOT, "  <wpt lat=\"%.7f\" lon=\"%.7f\">\n", lat, lon)
                + "    <name>" + escapeXml(name) + "</name>\n"
                + "  </wpt>\n"
                + "</gpx>\n";
    }

    /** A file name derived from the location name, safe on any filesystem. */
    public String fileName() {
        String base = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (base.isEmpty()) {
            base = DEFAULT_NAME;
        }
        if (base.length() > 60) {
            base = base.substring(0, 60);
        }
        return base + ".gpx";
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    /** Real network access via HttpURLConnection. */
    public static class HttpFetcher implements Fetcher {
        private static final int TIMEOUT_MS = 10000;

        private HttpURLConnection open(String url) throws IOException {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) gexporter");
            c.setRequestProperty("Accept-Language", "en");
            if (isGoogle(url)) {
                c.setRequestProperty("Cookie", GOOGLE_CONSENT_COOKIE);
            }
            return c;
        }

        @Override
        public String redirectTarget(String url) throws IOException {
            HttpURLConnection c = open(url);
            try {
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    return c.getHeaderField("Location");
                }
                return null;
            } finally {
                c.disconnect();
            }
        }
    }
}
