package org.surfsite.gexporter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
}
