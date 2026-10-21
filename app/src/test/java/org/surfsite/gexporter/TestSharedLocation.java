package org.surfsite.gexporter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestSharedLocation {
    private static final double EPS = 1e-7;

    private static void assertLoc(SharedLocation loc, String name, double lat, double lon) {
        assertNotNull("no location parsed", loc);
        assertEquals(name, loc.getName());
        assertEquals(lat, loc.getLat(), EPS);
        assertEquals(lon, loc.getLon(), EPS);
    }

    @Test
    public void geoUriPlain() {
        assertLoc(SharedLocation.parse("geo:48.2082,16.3738"), SharedLocation.DEFAULT_NAME, 48.2082, 16.3738);
    }

    @Test
    public void geoUriWithLabel() {
        assertLoc(SharedLocation.parse("geo:0,0?q=48.2082,16.3738(Stephansdom)"), "Stephansdom", 48.2082, 16.3738);
    }

    @Test
    public void geoUriWithEncodedLabelAndZoom() {
        assertLoc(SharedLocation.parse("geo:48.2,16.3?z=17&q=48.2082%2C16.3738%28Wiener%20Riesenrad%29"),
                "Wiener Riesenrad", 48.2082, 16.3738);
    }

    @Test
    public void geoUriAddressOnlyIsRejected() {
        assertNull(SharedLocation.parse("geo:0,0?q=Stephansplatz+3,+Wien"));
    }

    @Test
    public void googlePlacePrefersPlaceOverViewport() {
        String url = "https://www.google.com/maps/place/Stephansdom/@48.2080,16.3700,17z/"
                + "data=!3m1!4b1!4m6!3m5!1s0x476d079e5136ca9f:0xfdc2e58a51a25b46!8m2!3d48.2084!4d16.3731!16zL20vMDF4NGc";
        assertLoc(SharedLocation.parse(url), "Stephansdom", 48.2084, 16.3731);
    }

    @Test
    public void googleViewportOnly() {
        assertLoc(SharedLocation.parse("https://www.google.com/maps/@-33.8568,151.2153,15z"),
                SharedLocation.DEFAULT_NAME, -33.8568, 151.2153);
    }

    @Test
    public void googleQueryCoordinates() {
        assertLoc(SharedLocation.parse("https://maps.google.com/?q=48.2082,16.3738"),
                SharedLocation.DEFAULT_NAME, 48.2082, 16.3738);
        assertLoc(SharedLocation.parse("https://www.google.com/maps/search/?api=1&query=48.2082%2C16.3738"),
                SharedLocation.DEFAULT_NAME, 48.2082, 16.3738);
    }

    @Test
    public void droppedPinIsNotAName() {
        String url = "https://www.google.com/maps/place/48%C2%B012'29.5%22N+16%C2%B022'25.7%22E/@48.2082,16.3738,17z";
        assertLoc(SharedLocation.parse(url), SharedLocation.DEFAULT_NAME, 48.2082, 16.3738);
    }

    @Test
    public void nameFromSharedTextLine() {
        String text = "Stephansdom\nhttps://www.google.com/maps/@48.2084,16.3731,17z";
        assertLoc(SharedLocation.parse(text), "Stephansdom", 48.2084, 16.3731);
    }

    @Test
    public void plainCoordinates() {
        assertLoc(SharedLocation.parse("Treffpunkt\n48.2082, 16.3738"), "Treffpunkt", 48.2082, 16.3738);
    }

    @Test
    public void addressIsNotCoordinates() {
        assertNull(SharedLocation.parse("Hauptstraße 12, 34 Wien"));
    }

    @Test
    public void outOfRangeIsRejected() {
        assertNull(SharedLocation.parse("geo:91.0,16.3"));
    }

    // Expected values from organicmaps libs/ge0/ge0_tests/parser_tests.cpp
    @Test
    public void organicMapsGe0() {
        SharedLocation loc = SharedLocation.parse("ge0://B4srhdHVVt/Name");
        assertEquals("Name", loc.getName());
        assertEquals(64.5234, loc.getLat(), 1e-5);
        assertEquals(12.1234, loc.getLon(), 1e-5);

        loc = SharedLocation.parse("https://omaps.app/BaF6F6F6F6/Super_Poi");
        assertEquals("Super Poi", loc.getName());
        assertEquals(-20.0, loc.getLat(), 1e-5);
        assertEquals(20.0, loc.getLon(), 1e-5);

        assertEquals("zürich bahnhofstrasse",
                SharedLocation.parse("om://AwAAAAAAAA/z%c3%bcrich_bahnhofstrasse").getName());
        assertEquals("Hello", SharedLocation.parse("ge0://AwAAAAAAAA/Hello%%%%%%%%%World").getName());
        assertNull(SharedLocation.parse("ge0://Byqqqqqqq/Name"));
    }

    @Test
    public void organicMapsClearCoordinates() {
        assertLoc(SharedLocation.parse("https://omaps.app/48.208400,16.373100/Stephansdom?z=17"),
                "Stephansdom", 48.2084, 16.3731);
    }

    @Test
    public void organicMapsShareText() {
        // Layout of share::Build() in organicmaps libs/map/share.cpp; the ge0
        // code was generated with ge0::GenerateHttpShowMapUrl(48.2084, 16.3731, 17)
        String text = "Stephansdom\nStephansplatz 3, Wien\n48.208400, 16.373100\nhttps://omaps.app/04GXGEEwh0/Stephansdom";
        SharedLocation loc = SharedLocation.parse(text);
        assertEquals("Stephansdom", loc.getName());
        assertEquals(48.2084, loc.getLat(), 1e-5);
        assertEquals(16.3731, loc.getLon(), 1e-5);
    }

    @Test
    public void subjectDoesNotOverrideTextName() {
        // MainActivity appends EXTRA_SUBJECT after the text; Organic Maps'
        // subject is a phrase, the place name is the first line of the text.
        String text = "Stephansplatz 3, Wien\n48.208400, 16.373100\nhttps://omaps.app/04GXGEEwh0"
                + "\nEin Ort in Organic Maps";
        assertEquals("Stephansplatz 3, Wien", SharedLocation.parse(text).getName());
    }

    @Test
    public void organicMapsPageIsNoLocation() {
        assertNull(SharedLocation.parse("https://omaps.app/get"));
    }

    // Shape of a Google Maps "Share directions" link once its short link is
    // resolved: start, destination, the route's viewport, and in data= the
    // position of each named stop as !1d<lon>!2d<lat>.
    private static final String DIRECTIONS = "https://www.google.com/maps/dir/M%C3%BCnchen/Zugspitze/"
            + "@47.7869,11.0712,9z/data=!3m1!4b1!4m13!4m12"
            + "!1m5!1m1!1s0x479e75f9a38c5fd9:0x10cb84a7db1987d!2m2!1d11.5819806!2d48.1351253"
            + "!1m5!1m1!1s0x479d0b6a6d6e2a0b:0x2e8d7b9f2a3c!2m2!1d10.9863!2d47.4211";

    @Test
    public void directionsGoToTheDestinationNotTheViewport() {
        assertLoc(SharedLocation.parse(DIRECTIONS), "Zugspitze", 47.4211, 10.9863);
    }

    @Test
    public void directionsWithCoordinateStops() {
        assertLoc(SharedLocation.parse("https://www.google.com/maps/dir/48.1351253,11.5819806/47.4211,10.9863/@47.8,11.2,9z"),
                SharedLocation.DEFAULT_NAME, 47.4211, 10.9863);
    }

    @Test
    public void directionsApiLink() {
        assertLoc(SharedLocation.parse("https://www.google.com/maps/dir/?api=1&destination=47.4211,10.9863"),
                SharedLocation.DEFAULT_NAME, 47.4211, 10.9863);
    }

    @Test
    public void directionsWithoutPositionsAreGeocoded() throws Exception {
        String url = "https://www.google.com/maps/dir/M%C3%BCnchen/Zugspitze/@47.7869,11.0712,9z";
        assertNull(SharedLocation.parse(url));
        FakeFetcher f = new FakeFetcher();
        f.redirects.put("https://maps.app.goo.gl/dir123", url);
        f.bodies.put(nominatim("Zugspitze"), "[{\"lat\":\"47.4211\",\"lon\":\"10.9863\"}]");
        assertLoc(SharedLocation.resolve("https://maps.app.goo.gl/dir123", f), "Zugspitze", 47.4211, 10.9863);
    }

    @Test
    public void unknownLinkNeedsResolving() {
        assertNull(SharedLocation.parse("Stephansdom\nhttps://maps.app.goo.gl/abc123"));
    }

    private static class FakeFetcher implements SharedLocation.Fetcher {
        final Map<String, String> redirects = new HashMap<>();
        final Map<String, String> bodies = new HashMap<>();
        final List<String> fetched = new ArrayList<>();

        @Override
        public String redirectTarget(String url) {
            return redirects.get(url);
        }

        @Override
        public String body(String url) throws IOException {
            fetched.add(url);
            String body = bodies.get(url);
            if (body == null) {
                throw new IOException("404 " + url);
            }
            return body;
        }
    }

    private static String nominatim(String query) throws Exception {
        return SharedLocation.NOMINATIM + URLEncoder.encode(query, "UTF-8");
    }

    private static final String FRISTO_SHORT = "https://maps.app.goo.gl/snWQZ4aAnVYPzJ148";
    // Shape of a real Google Maps share (2026): a place URL with only name,
    // address and feature id, no coordinates.
    private static final String FRISTO_PLACE = "https://www.google.com/maps/place/FRISTO+Getr%C3%A4nkemarkt,"
            + "+Johann-Sebastian-Bach-Stra%C3%9Fe+5,+85591+Vaterstetten/data=!4m2!3m1"
            + "!1s0x479de27452cc669b:0xa06148bcc99c5f4f!18m1!1e1?entry=gps";

    @Test
    public void resolvesShortLinkRedirect() {
        FakeFetcher f = new FakeFetcher();
        f.redirects.put("https://maps.app.goo.gl/abc123",
                "https://www.google.com/maps/place/Stephansdom/data=!4m2!3m1!1s0x0:0x0!8m2!3d48.2084!4d16.3731");
        assertLoc(SharedLocation.resolve("Stephansdom, Wien\nhttps://maps.app.goo.gl/abc123", f),
                "Stephansdom", 48.2084, 16.3731);
    }

    @Test
    public void resolvesThroughConsentPage() {
        FakeFetcher f = new FakeFetcher();
        f.redirects.put("https://maps.app.goo.gl/abc123",
                "https://consent.google.com/m?continue=https%3A%2F%2Fwww.google.com%2Fmaps%2F%4048.2084%2C16.3731%2C17z&gl=AT");
        assertLoc(SharedLocation.resolve("https://maps.app.goo.gl/abc123", f),
                SharedLocation.DEFAULT_NAME, 48.2084, 16.3731);
    }

    @Test
    public void placeLinkIsGeocoded() throws Exception {
        FakeFetcher f = new FakeFetcher();
        f.redirects.put(FRISTO_SHORT, FRISTO_PLACE);
        // OSM does not know the shop name, only the address
        f.bodies.put(nominatim("FRISTO Getränkemarkt, Johann-Sebastian-Bach-Straße 5, 85591 Vaterstetten"), "[]");
        f.bodies.put(nominatim("Johann-Sebastian-Bach-Straße 5, 85591 Vaterstetten"),
                "[{\"lat\":\"48.1111247\",\"lon\":\"11.7789765\",\"display_name\":\"5, Johann-Sebastian-Bach-Straße\"}]");
        assertLoc(SharedLocation.resolve(FRISTO_SHORT, f), "FRISTO Getränkemarkt", 48.1111247, 11.7789765);
    }

    @Test
    public void geocodingStopsBeforeCityOnly() throws Exception {
        FakeFetcher f = new FakeFetcher();
        f.redirects.put(FRISTO_SHORT, FRISTO_PLACE);
        f.bodies.put(nominatim("FRISTO Getränkemarkt, Johann-Sebastian-Bach-Straße 5, 85591 Vaterstetten"), "[]");
        f.bodies.put(nominatim("Johann-Sebastian-Bach-Straße 5, 85591 Vaterstetten"), "[]");
        f.bodies.put(nominatim("85591 Vaterstetten"), "[{\"lat\":\"48.1\",\"lon\":\"11.7\"}]");
        assertNull(SharedLocation.resolve(FRISTO_SHORT, f));
        assertEquals(2, f.fetched.size());
    }

    @Test
    public void geocodingFailureGivesNull() {
        FakeFetcher f = new FakeFetcher();
        f.redirects.put(FRISTO_SHORT, FRISTO_PLACE);
        // no bodies: every request fails like a network error
        assertNull(SharedLocation.resolve(FRISTO_SHORT, f));
        assertEquals(1, f.fetched.size());
    }

    @Test
    public void malformedGeocoderReplyGivesNull() throws Exception {
        FakeFetcher f = new FakeFetcher();
        f.redirects.put(FRISTO_SHORT, FRISTO_PLACE);
        f.bodies.put(nominatim("FRISTO Getränkemarkt, Johann-Sebastian-Bach-Straße 5, 85591 Vaterstetten"),
                "<html>rate limited</html>");
        assertNull(SharedLocation.resolve(FRISTO_SHORT, f));
    }

    @Test
    public void coordinatesInLinkNeedNoGeocoding() {
        FakeFetcher f = new FakeFetcher();
        f.redirects.put(FRISTO_SHORT, "https://www.google.com/maps/place/FRISTO/@48.111,11.7789,17z");
        assertLoc(SharedLocation.resolve(FRISTO_SHORT, f), "FRISTO", 48.111, 11.7789);
        assertTrue(f.fetched.isEmpty());
    }

    @Test
    public void placeNameStopsAtAddress() {
        assertLoc(SharedLocation.parse("https://www.google.com/maps/place/FRISTO+Getr%C3%A4nkemarkt,"
                        + "+Johann-Sebastian-Bach-Stra%C3%9Fe+5,+85591+Vaterstetten/@48.111,11.7789,17z"),
                "FRISTO Getränkemarkt", 48.111, 11.7789);
    }

    @Test
    public void unresolvableGivesNull() {
        assertNull(SharedLocation.resolve("https://example.com/nothing", new FakeFetcher()));
    }

    @Test
    public void gpxRoundTripIsWaypointOnly() throws Exception {
        SharedLocation loc = new SharedLocation("Café <Zentral> & Co", 48.2104, 16.3655);
        File f = File.createTempFile("loc", ".gpx");
        f.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(loc.toGpx().getBytes(StandardCharsets.UTF_8));
        }
        try (FileInputStream in = new FileInputStream(f)) {
            Gpx2Fit gpx = new Gpx2Fit("loc", in, new Gpx2FitOptions());
            assertTrue(!gpx.hasTrackOrRoute());
            List<WayPoint> wpts = gpx.getWptPoints();
            assertEquals(1, wpts.size());
            assertEquals("Café <Zentral> & Co", wpts.get(0).getName());
            assertEquals(48.2104, wpts.get(0).getLat(), EPS);
            assertEquals(16.3655, wpts.get(0).getLon(), EPS);
        }
    }

    @Test
    public void watchMessage() {
        Map<String, Object> msg = new SharedLocation("Stephansdom", 48.2084, 16.3731)
                .toWatchMessage(1790000000123L);
        assertEquals("location", msg.get("type"));
        assertEquals("Stephansdom", msg.get("title"));
        assertEquals("48.2084000", msg.get("lat"));
        assertEquals("16.3731000", msg.get("lon"));
        // Integer, which ConnectIQ delivers as a Monkey C Number
        assertEquals(1790000000, msg.get("time"));
    }

    @Test
    public void fileNameIsSafe() {
        assertEquals("a_b_c.gpx", new SharedLocation("a/b:c", 0, 0).fileName());
    }
}
