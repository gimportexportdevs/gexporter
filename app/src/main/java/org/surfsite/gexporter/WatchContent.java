package org.surfsite.gexporter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The messages for managing gimporter's courses and locations on the watch.
 * gimporter can only list and delete content it created itself.
 *
 * <pre>
 * phone → watch  {"type": "list"}
 * phone → watch  {"type": "delete", "items": [[kind, id], ...], "time": epochSeconds}
 * phone → watch  {"type": "track", "url": file, "title": file, "port": n, "hash": fingerprint,
 *                  "time": epochSeconds}
 * watch → phone  {"type": "content", "items": [[kind, name, id, fingerprint], ...]}
 * watch → phone  {"type": "status", "state": "ready"|"importing"|"imported"|"started"|"failed", "title": track}
 * </pre>
 */
final class WatchContent {

    private WatchContent() {
    }

    /** One course, track, route or waypoint stored by gimporter's app or widget. */
    static final class Item {
        /** WatchLink.ownerOf() of the app or widget that stored it */
        final String owner;
        final String kind;
        final String name;
        final int id;
        /** gexporter's fingerprint it was imported with; "" if unknown */
        final String fingerprint;

        Item(String owner, String kind, String name, int id) {
            this(owner, kind, name, id, "");
        }

        Item(String owner, String kind, String name, int id, String fingerprint) {
            this.owner = owner;
            this.kind = kind;
            this.name = name;
            this.id = id;
            this.fingerprint = fingerprint;
        }

        boolean isLocation() {
            return "waypoint".equals(kind);
        }

        /** ids are only unique per kind and per owner */
        boolean sameAs(Item other) {
            return id == other.id && kind.equals(other.kind) && owner.equals(other.owner);
        }
    }

    static Map<String, Object> listRequest() {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "list");
        return msg;
    }

    /**
     * "time" lets the watch drop a request that waited in its mailbox while
     * gimporter was closed, instead of deleting things hours later.
     */
    static Map<String, Object> deleteRequest(List<Item> items, long nowMillis) {
        List<Object> pairs = new ArrayList<>();
        for (Item item : items) {
            pairs.add(Arrays.asList(item.kind, item.id));
        }
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "delete");
        msg.put("items", pairs);
        msg.put("time", (int) (nowMillis / 1000));
        return msg;
    }

    /**
     * Ask the watch to download and start a track now. url and title are the
     * served file name, as in dir.json with longname=1; port lets gimporter
     * skip its port handshake; time lets it drop a request left in its
     * mailbox.
     */
    static Map<String, Object> trackRequest(String url, String title, int port, String fingerprint, long nowMillis) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "track");
        msg.put("url", url);
        msg.put("title", title);
        msg.put("port", port);
        msg.put("hash", fingerprint);
        msg.put("time", (int) (nowMillis / 1000));
        return msg;
    }

    /**
     * Whether a served track or location is on the watch: gimporter reports
     * the fingerprint it was imported with. Matching by name would also mark
     * a different track that happens to have the same title.
     */
    static boolean isOnWatch(String fingerprint, List<Item> watchItems) {
        if (fingerprint == null || fingerprint.isEmpty()) {
            return false;
        }
        for (Item item : watchItems) {
            if (fingerprint.equals(item.fingerprint)) {
                return true;
            }
        }
        return false;
    }

    /** Fingerprint of a track: a hash of the file's content. */
    static String fingerprint(File file) {
        MessageDigest sha = sha1();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                sha.update(buf, 0, len);
            }
        } catch (IOException e) {
            return "";
        }
        return hex(sha.digest());
    }

    /** Fingerprint of a location: a hash of its name and coordinates. */
    static String fingerprint(String name, double lat, double lon) {
        MessageDigest sha = sha1();
        sha.update(String.format(Locale.ROOT, "%s|%.7f|%.7f", name, lat, lon).getBytes(StandardCharsets.UTF_8));
        return hex(sha.digest());
    }

    private static MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);  // every Java platform has SHA-1
        }
    }

    /** The first 8 bytes in hex: plenty to tell a user's tracks apart. */
    private static String hex(byte[] digest) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(String.format(Locale.ROOT, "%02x", digest[i]));
        }
        return sb.toString();
    }

    /** A "status" message: what gimporter is doing, see WatchLink.isBusy(). */
    static final class Status {
        final String state;
        final String title;

        Status(String state, String title) {
            this.state = state;
            this.title = title;
        }
    }

    /** The status of a "status" message, or null if the message is something else. */
    static Status parseStatus(Object message) {
        if (!(message instanceof Map)) {
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) message;
        if (!"status".equals(map.get("type")) || !(map.get("state") instanceof String)) {
            return null;
        }
        Object title = map.get("title");
        return new Status((String) map.get("state"), title instanceof String ? (String) title : "");
    }

    /**
     * The items of a "content" reply, or null if the message is something
     * else. Malformed entries are skipped.
     */
    static List<Item> parseContent(Object message, String owner) {
        if (!(message instanceof Map)) {
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) message;
        if (!"content".equals(map.get("type"))) {
            return null;
        }
        Object items = map.get("items");
        if (!(items instanceof List)) {
            return Collections.emptyList();
        }
        List<Item> result = new ArrayList<>();
        for (Object entry : (List<?>) items) {
            // gimporter before fingerprints sends 3 elements
            if (!(entry instanceof List) || ((List<?>) entry).size() < 3) {
                continue;
            }
            List<?> e = (List<?>) entry;
            if (e.get(0) instanceof String && e.get(1) instanceof String && e.get(2) instanceof Number) {
                String fp = e.size() > 3 && e.get(3) instanceof String ? (String) e.get(3) : "";
                result.add(new Item(owner, (String) e.get(0), (String) e.get(1), ((Number) e.get(2)).intValue(), fp));
            }
        }
        return result;
    }
}
