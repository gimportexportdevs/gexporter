package org.surfsite.gexporter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The messages for managing gimporter's courses and locations on the watch.
 * gimporter can only list and delete content it created itself.
 *
 * <pre>
 * phone → watch  {"type": "list"}
 * phone → watch  {"type": "delete", "items": [[kind, id], ...], "time": epochSeconds}
 * watch → phone  {"type": "content", "items": [[kind, name, id], ...]}
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

        Item(String owner, String kind, String name, int id) {
            this.owner = owner;
            this.kind = kind;
            this.name = name;
            this.id = id;
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
            if (!(entry instanceof List) || ((List<?>) entry).size() != 3) {
                continue;
            }
            List<?> e = (List<?>) entry;
            if (e.get(0) instanceof String && e.get(1) instanceof String && e.get(2) instanceof Number) {
                result.add(new Item(owner, (String) e.get(0), (String) e.get(1), ((Number) e.get(2)).intValue()));
            }
        }
        return result;
    }
}
