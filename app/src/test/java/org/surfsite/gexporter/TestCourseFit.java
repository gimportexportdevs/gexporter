package org.surfsite.gexporter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.garmin.fit.CoursePointMesg;
import com.garmin.fit.Decode;
import com.garmin.fit.Event;
import com.garmin.fit.EventMesg;
import com.garmin.fit.EventType;
import com.garmin.fit.MesgBroadcaster;
import com.garmin.fit.RecordMesg;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes the written course to check what devices rely on: course points
 * carry their distance along the course, and no timestamp is before the FIT
 * epoch.
 */
public class TestCourseFit {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final List<RecordMesg> records = new ArrayList<>();
    private final List<CoursePointMesg> coursePoints = new ArrayList<>();
    private final List<EventMesg> events = new ArrayList<>();

    /** A straight track north, 0.01° (about 1.1 km) per point. */
    private File gpx(String wpts, String timeFormat) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<gpx version=\"1.1\" creator=\"test\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
                .append(wpts)
                .append("<trk><name>t</name><trkseg>\n");
        for (int i = 0; i <= 10; i++) {
            sb.append(String.format(java.util.Locale.ROOT, "<trkpt lat=\"%.2f\" lon=\"9.0\">", 48.0 + i * 0.01));
            if (timeFormat != null) {
                sb.append("<time>").append(String.format(timeFormat, i)).append("</time>");
            }
            sb.append("</trkpt>\n");
        }
        sb.append("</trkseg></trk></gpx>\n");
        File f = tmp.newFile("in.gpx");
        try (FileWriter w = new FileWriter(f)) {
            w.write(sb.toString());
        }
        return f;
    }

    private void convertAndDecode(File gpx) throws Exception {
        File fit = tmp.newFile("out.fit");
        try (FileInputStream in = new FileInputStream(gpx)) {
            new Gpx2Fit("t", in, new Gpx2FitOptions()).writeFit(fit);
        }
        Decode decode = new Decode();
        MesgBroadcaster broadcaster = new MesgBroadcaster(decode);
        broadcaster.addListener((com.garmin.fit.RecordMesgListener) records::add);
        broadcaster.addListener((com.garmin.fit.CoursePointMesgListener) coursePoints::add);
        broadcaster.addListener((com.garmin.fit.EventMesgListener) events::add);
        try (InputStream in = new FileInputStream(fit)) {
            assertTrue("FIT integrity", decode.checkFileIntegrity(in));
        }
        try (InputStream in = new FileInputStream(fit)) {
            decode.read(in, broadcaster, broadcaster);
        }
    }

    @Test
    public void waypointsGetTheirDistanceAlongTheCourse() throws Exception {
        // listed out of order, both slightly off the track
        convertAndDecode(gpx(
                "<wpt lat=\"48.0801\" lon=\"9.0001\"><name>late</name></wpt>\n"
                        + "<wpt lat=\"48.0199\" lon=\"8.9999\"><name>early</name></wpt>\n",
                null));

        assertEquals(2, coursePoints.size());
        CoursePointMesg early = coursePoints.get(0);
        CoursePointMesg late = coursePoints.get(1);
        assertEquals("early", early.getName());
        assertEquals("late", late.getName());
        assertNotNull(early.getDistance());
        // nearest track points are #2 and #8
        assertEquals(records.get(2).getDistance(), early.getDistance(), 0.5f);
        assertEquals(records.get(8).getDistance(), late.getDistance(), 0.5f);
        assertEquals(records.get(2).getTimestamp().getTimestamp(), early.getTimestamp().getTimestamp());
        assertEquals(records.get(8).getTimestamp().getTimestamp(), late.getTimestamp().getTimestamp());
    }

    @Test
    public void timesRelativeTo1970AreMovedToTheFitEpoch() throws Exception {
        // Locus Map writes route times relative to 1970-01-01 (issue #36)
        convertAndDecode(gpx("", "1970-01-01T00:%02d:00.000Z"));

        assertEquals(11, records.size());
        long first = records.get(0).getTimestamp().getTimestamp();
        assertEquals(0L, first);  // the FIT epoch
        for (int i = 0; i < records.size(); i++) {
            // one minute per point, as in the GPX
            assertEquals(first + i * 60L, (long) records.get(i).getTimestamp().getTimestamp());
        }
    }

    @Test
    public void currentTimesAreKept() throws Exception {
        convertAndDecode(gpx("", "2026-09-05T10:%02d:00Z"));

        // 2026-09-05T10:00:00Z in FIT seconds (since 1989-12-31T00:00:00Z)
        long expected = (1788602400000L - 631065600000L) / 1000L;
        assertEquals(expected, (long) records.get(0).getTimestamp().getTimestamp());
    }

    @Test
    public void courseEndsWithTimerStopAllAtTheLastRecord() throws Exception {
        // as Garmin's EncodeCourse example
        convertAndDecode(gpx("", "2026-09-05T10:%02d:00Z"));

        EventMesg stop = events.get(events.size() - 1);
        assertEquals(Event.TIMER, stop.getEvent());
        assertEquals(EventType.STOP_ALL, stop.getEventType());
        assertEquals(records.get(records.size() - 1).getTimestamp().getTimestamp(),
                stop.getTimestamp().getTimestamp());
    }
}
