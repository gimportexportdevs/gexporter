package org.surfsite.gpxtofit;

import android.util.Xml;

import com.garmin.fit.CourseMesg;
import com.garmin.fit.CoursePoint;
import com.garmin.fit.CoursePointMesg;
import com.garmin.fit.DateTime;
import com.garmin.fit.Event;
import com.garmin.fit.EventMesg;
import com.garmin.fit.EventType;
import com.garmin.fit.FileEncoder;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.Fit;
import com.garmin.fit.LapMesg;
import com.garmin.fit.Manufacturer;
import com.garmin.fit.RecordMesg;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class GPXLoader {
    public static final String HTTP_WWW_TOPOGRAFIX_COM_GPX_1_0 = "http://www.topografix.com/GPX/1/0";
    public static final String HTTP_WWW_TOPOGRAFIX_COM_GPX_1_1 = "http://www.topografix.com/GPX/1/1";

    private double sdist = .0;
    private WayPoint last = null;
    private double minEle = 100000.0;
    private double maxEle = .0;
    private double totalAsc = .0;
    private double totalDesc = .0;
    private WayPoint firstWayPoint = null;
    private WayPoint lastWayPoint = null;
    private final long today = (new Date()).getTime();
    private long i = 0;
    private double dist = .0;
    private double lcdist = .0;
    private double ldist = .0;
    private long duration = 0;
    private CoursePointMesg cp;
    private RecordMesg r;
    private FileEncoder encode;
    private List<WayPoint> wayPoints = new ArrayList<>();
    private String courseName = new String("course");
    private String ns = HTTP_WWW_TOPOGRAFIX_COM_GPX_1_0;
    
    public GPXLoader(File file) throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        //parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);

        FileInputStream in = new FileInputStream(file);

        try {
            parser.setInput(in, null);
            parser.nextTag();
            readFeed10(parser);
            in.close();
            return;
        } catch (Exception e) {
        	ns = HTTP_WWW_TOPOGRAFIX_COM_GPX_1_1;
        } finally {
            in.close();
        }

        in = new FileInputStream(file);

        try {
            parser.setInput(in, null);
            parser.nextTag();
            readFeed10(parser);
        } finally {
            in.close();
        }
    }

    public List<WayPoint> getWaypoints() {
    	return wayPoints;
    }
    
    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

    private void readFeed10(XmlPullParser parser) throws XmlPullParserException, IOException, ParseException {
        parser.require(XmlPullParser.START_TAG, ns, "gpx");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the entry tag
            if (name.equals("trk")) {
                readTrk10(parser);
            } else {
                skip(parser);
            }
        }
        return;
    }

    private void readTrk10(XmlPullParser parser) throws XmlPullParserException, IOException, ParseException {
        parser.require(XmlPullParser.START_TAG, ns, "trk");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the entry tag
            if (name.equals("trkseg")) {
                readTrkSeg10(parser);
            } else if (name.equals("name")) {
            	courseName = readTrkName10(parser);
            } else {
                skip(parser);
            }
        }
    }

    private String readTrkName10(XmlPullParser parser) throws IOException, XmlPullParserException, ParseException {
        parser.require(XmlPullParser.START_TAG, ns, "name");
        String txt = readText(parser);
        if (txt.length() > 15)
        	txt = txt.substring(0, 15);
        parser.require(XmlPullParser.END_TAG, ns, "name");
        return txt;
    }

    private void readTrkSeg10(XmlPullParser parser) throws XmlPullParserException, IOException, ParseException {
        parser.require(XmlPullParser.START_TAG, ns, "trkseg");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the entry tag
            if (name.equals("trkpt")) {
                readTrkPt10(parser);
            } else {
                skip(parser);
            }
        }
    }

    // Parses the contents of an entry. If it encounters a title, summary, or link tag, hands them off
// to their respective "read" methods for processing. Otherwise, skips the tag.
    private void readTrkPt10(XmlPullParser parser) throws XmlPullParserException, IOException, ParseException {
        parser.require(XmlPullParser.START_TAG, ns, "trkpt");
        double ele = .0;
        Date time = new Date();
        double lat = .0;
        double lon = .0;
        lat = Double.parseDouble(parser.getAttributeValue(null, "lat"));
        lon = Double.parseDouble(parser.getAttributeValue(null, "lon"));

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("ele")) {
                ele = readEle(parser);
            } else if (name.equals("time")) {
                time = readTime(parser);
                if (time == null)
                    time = new Date();
            } else {
                skip(parser);
            }
        }
        wayPoints.add(new WayPoint(lat, lon, ele, time));
        return;
    }

    private double readEle(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, "ele");
        String txt = readText(parser);
        double ele = Double.parseDouble(txt);
        parser.require(XmlPullParser.END_TAG, ns, "ele");
        return ele;
    }

    private Date readTime(XmlPullParser parser) throws IOException, XmlPullParserException, ParseException {
        parser.require(XmlPullParser.START_TAG, ns, "time");
        String txt = readText(parser);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        Date time = dateFormat.parse(txt);
        parser.require(XmlPullParser.END_TAG, ns, "time");
        return time;
    }

    private String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    public String getName() {
        return courseName;
    }

    public void writeFit(File outfile) {
        sdist = .0;
        last = null;
        minEle = 100000.0;
        maxEle = .0;
        totalAsc = .0;
        totalDesc = .0;
        firstWayPoint = null;
        lastWayPoint = null;
        i = 0;
        dist = .0;
        lcdist = .0;
        ldist = .0;
        duration = 0;

        encode = new FileEncoder(outfile, Fit.ProtocolVersion.V2_0);

        //Generate FileIdMessage
        FileIdMesg fileIdMesg = new FileIdMesg(); // Every FIT file MUST contain a 'File ID' message as the first message
        fileIdMesg.setManufacturer(Manufacturer.DYNASTREAM);
        fileIdMesg.setType(com.garmin.fit.File.COURSE);
        fileIdMesg.setProduct(12345);
        fileIdMesg.setSerialNumber(12345L);
        encode.write(fileIdMesg); // Encode the FileIDMesg

        CourseMesg courseMesg = new CourseMesg();
        courseMesg.setLocalNum(1);
        courseMesg.setName(getName());
        encode.write(courseMesg);

        LapMesg lapMesg = new LapMesg();
        lapMesg.setLocalNum(2);
        EventMesg eventMesg = new EventMesg();
        eventMesg.setLocalNum(3);
        r = new RecordMesg();
        r.setLocalNum(4);
        cp = new CoursePointMesg();
        cp.setLocalNum(5);

        System.out.println(String.format("Track: %s", getName()));
        firstWayPoint = wayPoints.get(0);
        lastWayPoint = wayPoints.get(wayPoints.size() - 1);

        for (WayPoint wpt : wayPoints) {
            if (minEle > wpt.getEle())
                minEle = wpt.getEle();
            if (maxEle < wpt.getEle())
                maxEle = wpt.getEle();
            if (last != null) {
                sdist += wpt.distance3D(last);
                double ele = wpt.getEle() - last.getEle();
                if (ele > 0.0)
                    totalAsc += ele;
                else
                    totalDesc += Math.abs(ele);

            }
            last = wpt;
            System.out.println(String.format("[%s , %s] %s", wpt.getLat(), wpt.getLon(), wpt.getEle()));
        }

        totalAsc += 0.5;
        totalDesc += 0.5;
        lapMesg.setTotalDistance((float) sdist);
        lapMesg.setMaxAltitude((float) maxEle);
        lapMesg.setMinAltitude((float) minEle);
        lapMesg.setTotalAscent((int) totalAsc);
        lapMesg.setTotalDescent((int) totalDesc);
        WayPoint w = firstWayPoint;
        lapMesg.setStartPositionLat(Utils.toSemiCircles(w.getLat()));
        lapMesg.setStartPositionLong(Utils.toSemiCircles(w.getLon()));
        Date startDate = w.getTime();
        lapMesg.setStartTime(new DateTime(startDate));

        w = lastWayPoint;
        lapMesg.setEndPositionLat(Utils.toSemiCircles(w.getLat()));
        lapMesg.setEndPositionLong(Utils.toSemiCircles(w.getLon()));
        Date endDate = w.getTime();

        duration = endDate.getTime() - startDate.getTime();
        lapMesg.setTotalElapsedTime((float) (duration / 1000.0));
        lapMesg.setTotalTimerTime((float) (duration / 1000.0));

        if (duration != 0)
            lapMesg.setAvgSpeed((float) (sdist * 1000.0 / (double) duration));

        encode.write(lapMesg);
        sdist = sdist / 48.0;
        if (sdist < 50.0)
            sdist = 50.0;

        eventMesg.setEvent(Event.TIMER);
        eventMesg.setEventType(EventType.START);
        eventMesg.setEventGroup((short) 0);
        eventMesg.setTimestamp(new DateTime(startDate));
        encode.write(eventMesg);
        last = null;

        for (WayPoint wpt : wayPoints) {
            Date timestamp;
            i += 1;

            if (duration != 0)
                timestamp = wpt.getTime();
            else
                timestamp = new Date(today + i * 1000);

            if (last == null) {
                cp.setPositionLat(Utils.toSemiCircles(wpt.getLat()));
                cp.setPositionLong(Utils.toSemiCircles(wpt.getLon()));
                cp.setName("Start");
                cp.setType(CoursePoint.GENERIC);
                cp.setDistance((float) dist);
                cp.setTimestamp(new DateTime(timestamp));

                encode.write(cp);
            } else {
                dist += wpt.distance3D(last);
            }

            if (wpt == lastWayPoint) {
                cp.setPositionLat(Utils.toSemiCircles(wpt.getLat()));
                cp.setPositionLong(Utils.toSemiCircles(wpt.getLon()));
                cp.setName("End");
                cp.setType(CoursePoint.GENERIC);
                cp.setDistance((float) dist);
                cp.setTimestamp(new DateTime(timestamp));

                encode.write(cp);
            } else if ((dist - lcdist) > sdist) {
                cp.setName("");
                cp.setType(CoursePoint.GENERIC);
                cp.setPositionLat(Utils.toSemiCircles(wpt.getLat()));
                cp.setPositionLong(Utils.toSemiCircles(wpt.getLon()));
                cp.setDistance((float) dist);
                cp.setTimestamp(new DateTime(timestamp));

                encode.write(cp);
                lcdist = dist;
            }
            if ((last == null) || (dist - ldist) > 5.0) {
                r.setPositionLat(Utils.toSemiCircles(wpt.getLat()));
                r.setPositionLong(Utils.toSemiCircles(wpt.getLon()));
                r.setDistance((float) dist);
                r.setAltitude((float) wpt.getEle());
                r.setTimestamp(new DateTime(timestamp));
                encode.write(r);
                ldist = dist;
            }

            last = wpt;
            System.out.println(String.format("[%s , %s] %s - %f", wpt.getLat(), wpt.getLon(), wpt.getEle(), dist));
        }

        eventMesg.setEvent(Event.TIMER);
        eventMesg.setEventType(EventType.STOP_DISABLE_ALL);
        eventMesg.setEventGroup((short) 0);
        eventMesg.setTimestamp(new DateTime(startDate));
        encode.write(eventMesg);
        encode.close();
    }
}
