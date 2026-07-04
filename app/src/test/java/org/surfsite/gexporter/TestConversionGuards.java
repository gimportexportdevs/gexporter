package org.surfsite.gexporter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.TimeZone;

/**
 * Covers the input-validation and timestamp-parsing fixes that the golden
 * FIT samples (untimed routes) do not exercise.
 */
public class TestConversionGuards {

    private static long utc(int y, int mo, int d, int h, int mi, int s, int ms) {
        java.util.Calendar c = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(y, mo - 1, d, h, mi, s);
        c.set(java.util.Calendar.MILLISECOND, ms);
        return c.getTimeInMillis();
    }

    @Test
    public void parsesBasicUtc() {
        assertEquals(utc(2019, 6, 1, 10, 0, 0, 0),
                Gpx2Fit.parseIsoTime("2019-06-01T10:00:00Z").getTime());
    }

    @Test
    public void parsesFractionalSeconds() {
        assertEquals(utc(2019, 6, 1, 10, 0, 0, 250),
                Gpx2Fit.parseIsoTime("2019-06-01T10:00:00.250Z").getTime());
    }

    @Test
    public void parsesNumericOffset() {
        // 10:00 at +01:00 is 09:00 UTC
        assertEquals(utc(2019, 6, 1, 9, 0, 0, 0),
                Gpx2Fit.parseIsoTime("2019-06-01T10:00:00+01:00").getTime());
    }

    @Test
    public void zuluIsTreatedAsUtcNotLocal() {
        // Regression: the old SimpleDateFormat treated 'Z' as a literal and
        // parsed in the device zone. Force a non-UTC default and confirm the
        // result is still UTC-anchored.
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            assertEquals(utc(2019, 6, 1, 10, 0, 0, 0),
                    Gpx2Fit.parseIsoTime("2019-06-01T10:00:00Z").getTime());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void rejectsUnparseableAndEmpty() {
        assertNull(Gpx2Fit.parseIsoTime("not a date"));
        assertNull(Gpx2Fit.parseIsoTime(""));
        assertNull(Gpx2Fit.parseIsoTime(null));
        // lenient=false: nonsense field values must not "roll over" to a date
        assertNull(Gpx2Fit.parseIsoTime("2017-13-45T99:99:99Z"));
    }

    @Test
    public void emptyTrackIsRejectedNotCrashed() throws Exception {
        java.io.File gpx = java.io.File.createTempFile("empty", ".gpx");
        try (java.io.FileWriter w = new java.io.FileWriter(gpx)) {
            w.write("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\">"
                    + "<trk><trkseg></trkseg></trk></gpx>");
        }
        Gpx2Fit loader = new Gpx2Fit("empty",
                new java.io.FileInputStream(gpx), new Gpx2FitOptions());
        try {
            loader.writeFit(java.io.File.createTempFile("empty", ".fit"));
            fail("expected IllegalArgumentException for empty track");
        } catch (IllegalArgumentException expected) {
            // correct: guarded instead of IndexOutOfBounds after opening the encoder
        } catch (Exception e) {
            fail("expected IllegalArgumentException, got " + e);
        }
    }
}
