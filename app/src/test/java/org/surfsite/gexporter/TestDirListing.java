package org.surfsite.gexporter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** dir.json lists waypoint-only GPX files as locations with coordinates. */
public class TestDirListing {
    private File root;
    private File cache;
    private WebServer server;

    @Before
    public void setUp() throws Exception {
        root = Files.createTempDirectory("root").toFile();
        cache = Files.createTempDirectory("cache").toFile();

        try (OutputStream out = new FileOutputStream(new File(root, "Stephansdom.gpx"))) {
            out.write(new SharedLocation("Stephansdom", 48.2084, 16.3731).toGpx()
                    .getBytes(StandardCharsets.UTF_8));
        }
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("sample11.gpx")) {
            Files.copy(in, new File(root, "track.gpx").toPath());
        }

        server = new WebServer(root, cache, 0, new Gpx2FitOptions());
        server.start();
    }

    @After
    public void tearDown() {
        server.stop();
        for (File d : new File[]{root, cache}) {
            File[] files = d.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            d.delete();
        }
    }

    private JsonArray listing(String query) throws Exception {
        URL url = new URL("http://127.0.0.1:" + server.getListeningPort() + "/dir.json?" + query);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        try (InputStream in = c.getInputStream()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("tracks");
        } finally {
            c.disconnect();
        }
    }

    @Test
    public void locationHasCoordinatesTrackDoesNot() throws Exception {
        for (String query : new String[]{"type=FIT&short=1&longname=1&locations=1",
                "type=GPX&short=1&longname=1&locations=1"}) {
            JsonArray tracks = listing(query);
            assertEquals(query, 2, tracks.size());

            JsonObject loc = tracks.get(0).getAsJsonObject();
            assertEquals("Stephansdom", loc.get("title").getAsString());
            assertEquals("Stephansdom.gpx", loc.get("url").getAsString());
            assertEquals("48.2084000", loc.get("lat").getAsString());
            assertEquals("16.3731000", loc.get("lon").getAsString());

            JsonObject track = tracks.get(1).getAsJsonObject();
            assertEquals("track.gpx", track.get("title").getAsString());
            assertFalse(track.has("lat"));
            assertTrue(track.has("url"));
        }
    }

    @Test
    public void olderGimporterGetsNoLocations() throws Exception {
        // without locations=1 the watch would try to import it as a course
        JsonArray tracks = listing("type=FIT&short=1&longname=1");
        assertEquals(1, tracks.size());
        assertEquals("track.gpx", tracks.get(0).getAsJsonObject().get("title").getAsString());
    }
}
