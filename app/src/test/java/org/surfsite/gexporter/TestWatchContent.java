package org.surfsite.gexporter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestWatchContent {

    @Test
    public void listRequest() {
        assertEquals("list", WatchContent.listRequest().get("type"));
    }

    @Test
    public void deleteRequestCarriesKindIdAndTime() {
        Map<String, Object> msg = WatchContent.deleteRequest(Arrays.asList(
                new WatchContent.Item("app", "course", "Tour", 101),
                new WatchContent.Item("app", "waypoint", "Dom", 7)), 1790000000123L);
        assertEquals("delete", msg.get("type"));
        assertEquals(Arrays.asList(Arrays.asList("course", 101), Arrays.asList("waypoint", 7)), msg.get("items"));
        assertEquals(1790000000, msg.get("time"));
    }

    @Test
    public void parsesContentReply() {
        // what the ConnectIQ companion SDK hands over for the watch's Dictionary
        Map<String, Object> reply = new HashMap<>();
        reply.put("type", "content");
        reply.put("items", Arrays.asList(
                Arrays.asList("course", "Tour", 101),
                Arrays.asList("waypoint", "Dom", 7L),
                Arrays.asList("course", "broken"),
                "garbage"));
        List<WatchContent.Item> items = WatchContent.parseContent(reply, "widget");
        assertEquals(2, items.size());
        assertEquals("Tour", items.get(0).name);
        assertEquals(101, items.get(0).id);
        assertFalse(items.get(0).isLocation());
        assertTrue(items.get(1).isLocation());
        assertEquals(7, items.get(1).id);
        assertEquals("widget", items.get(0).owner);
    }

    @Test
    public void sameItemNeedsSameOwner() {
        WatchContent.Item app = new WatchContent.Item("app", "course", "Tour", 1);
        assertTrue(app.sameAs(new WatchContent.Item("app", "course", "Tour", 1)));
        assertFalse(app.sameAs(new WatchContent.Item("widget", "course", "Tour", 1)));
        assertFalse(app.sameAs(new WatchContent.Item("app", "waypoint", "Tour", 1)));
    }

    @Test
    public void ownerIgnoresDashesAndCase() {
        assertEquals(WatchLink.ownerOf("9B0A09CF-C89E-4F7C-A5E4-AB21400EE424"),
                WatchLink.ownerOf("9B0A09CFC89E4F7CA5E4AB21400EE424"));
    }

    @Test
    public void otherMessagesAreNotContent() {
        assertNull(WatchContent.parseContent("GET_PORT", "app"));
        Map<String, Object> other = new HashMap<>();
        other.put("type", "something");
        assertNull(WatchContent.parseContent(other, "app"));
    }

    @Test
    public void contentWithoutItemsIsEmpty() {
        Map<String, Object> reply = new HashMap<>();
        reply.put("type", "content");
        assertTrue(WatchContent.parseContent(reply, "widget").isEmpty());
    }

    @Test
    public void trackRequest() {
        Map<String, Object> msg = WatchContent.trackRequest("Tour%20A.gpx", "Tour A.gpx", 22222, "0123abcd", 1790000000123L);
        assertEquals("track", msg.get("type"));
        assertEquals("Tour%20A.gpx", msg.get("url"));
        assertEquals("Tour A.gpx", msg.get("title"));
        assertEquals(22222, msg.get("port"));
        assertEquals("0123abcd", msg.get("hash"));
        assertEquals(1790000000, msg.get("time"));
    }

    @Test
    public void onWatchByFingerprintNotByName() {
        List<WatchContent.Item> watch = Arrays.asList(
                new WatchContent.Item("app", "course", "Tour", 1, "aaaa"),
                // imported before fingerprints: unknown
                new WatchContent.Item("app", "course", "Runde", 2, ""));
        assertTrue(WatchContent.isOnWatch("aaaa", watch));
        // same title, different content
        assertFalse(WatchContent.isOnWatch("bbbb", watch));
        assertFalse(WatchContent.isOnWatch("", watch));
        assertFalse(WatchContent.isOnWatch(null, watch));
    }

    @Test
    public void fingerprints() throws Exception {
        File a = File.createTempFile("fp-a", ".gpx");
        File b = File.createTempFile("fp-b", ".gpx");
        try {
            Files.write(a.toPath(), "<gpx>one</gpx>".getBytes(StandardCharsets.UTF_8));
            Files.write(b.toPath(), "<gpx>two</gpx>".getBytes(StandardCharsets.UTF_8));
            assertEquals(16, WatchContent.fingerprint(a).length());
            assertEquals(WatchContent.fingerprint(a), WatchContent.fingerprint(a));
            assertFalse(WatchContent.fingerprint(a).equals(WatchContent.fingerprint(b)));
        } finally {
            a.delete();
            b.delete();
        }
        assertEquals(WatchContent.fingerprint("Dom", 48.2084, 16.3731),
                WatchContent.fingerprint("Dom", 48.20840000001, 16.3731));
        assertFalse(WatchContent.fingerprint("Dom", 48.2084, 16.3731)
                .equals(WatchContent.fingerprint("Dom", 48.2085, 16.3731)));
    }

    @Test
    public void contentWithFingerprints() {
        Map<String, Object> reply = new HashMap<>();
        reply.put("type", "content");
        reply.put("items", Arrays.asList(Arrays.asList("course", "Tour", 1, "aaaa"), Arrays.asList("course", "Old", 2)));
        List<WatchContent.Item> items = WatchContent.parseContent(reply, "app");
        assertEquals("aaaa", items.get(0).fingerprint);
        assertEquals("", items.get(1).fingerprint);
    }

    @Test
    public void statusMessage() {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "status");
        msg.put("state", "importing");
        msg.put("title", "Tour.gpx");
        WatchContent.Status status = WatchContent.parseStatus(msg);
        assertEquals("importing", status.state);
        assertEquals("Tour.gpx", status.title);
        assertNull(WatchContent.parseStatus("GET_PORT"));
        msg.put("type", "content");
        assertNull(WatchContent.parseStatus(msg));
    }

    @Test
    public void busyOnlyWhileImporting() {
        WatchLink.dispatchStatus(new WatchContent.Status("importing", "Tour.gpx"), 1000);
        assertEquals("Tour.gpx", WatchLink.busyWith(2000));
        // no "started" or "failed" within a minute: not busy any more
        assertNull(WatchLink.busyWith(1000 + 61000));
        WatchLink.dispatchStatus(new WatchContent.Status("started", "Tour.gpx"), 3000);
        assertNull(WatchLink.busyWith(4000));
        WatchLink.clear();
    }
}
