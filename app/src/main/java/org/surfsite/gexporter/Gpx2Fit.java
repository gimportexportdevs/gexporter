package org.surfsite.gexporter;

import android.annotation.SuppressLint;

import com.garmin.fit.CourseMesg;
import com.garmin.fit.CoursePoint;
import com.garmin.fit.CoursePointMesg;
import com.garmin.fit.DateTime;
import com.garmin.fit.Event;
import com.garmin.fit.EventMesg;
import com.garmin.fit.EventType;
import com.garmin.fit.Field;
import com.garmin.fit.FileEncoder;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.Fit;
import com.garmin.fit.LapMesg;
import com.garmin.fit.Manufacturer;
import com.garmin.fit.Profile;
import com.garmin.fit.RecordMesg;
import com.garmin.fit.Sport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

public class Gpx2Fit {
    private static final Logger Log = LoggerFactory.getLogger(Gpx2Fit.class);

    private static final String HTTP_WWW_TOPOGRAFIX_COM_GPX_1_0 = "http://www.topografix.com/GPX/1/0";
    private static final String HTTP_WWW_TOPOGRAFIX_COM_GPX_1_1 = "http://www.topografix.com/GPX/1/1";

    private final List<WayPoint> trkPoints = new ArrayList<>();
    private final List<WayPoint> rtePoints = new ArrayList<>();
    private final List<WayPoint> wayPoints = new ArrayList<>();
    private final String courseName;
    private String ns = HTTP_WWW_TOPOGRAFIX_COM_GPX_1_1;

    Gpx2FitOptions mGpx2FitOptions;

    public Gpx2Fit(String name, FileInputStream in, Gpx2FitOptions options) throws Exception {
        mGpx2FitOptions = options;
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        //parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        courseName = name;

        BufferedInputStream inputStream = new BufferedInputStream(in);

        try {
            parser.setInput(inputStream, null);
            parser.nextTag();
            readGPX(parser);
            inputStream.close();
            return;
        } catch (Exception e) {
            ns = HTTP_WWW_TOPOGRAFIX_COM_GPX_1_0;
            if (Log.isDebugEnabled())
                Log.debug("Ex {}", e);
        }

        in.getChannel().position(0);
        inputStream = new BufferedInputStream(in);

        try {
            parser.setInput(inputStream, null);
            parser.nextTag();
            readGPX(parser);
        } finally {
            inputStream.close();
        }
    }

    public List<WayPoint> getWaypoints() {
        return trkPoints;
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

    private void readGPX(XmlPullParser parser) throws XmlPullParserException, IOException, ParseException {
        parser.require(XmlPullParser.START_TAG, ns, "gpx");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the entry tag
            switch (name) {
                case "trk":
                    readTrk(parser);
                    break;
                case "rte":
                    readRte(parser);
                    break;
                case "wpt":
                    readWpt(parser);
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
    }

    private void readTrk(XmlPullParser parser) throws XmlPullParserException, IOException, ParseException {
        parser.require(XmlPullParser.START_TAG, ns, "trk");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the entry tag
            switch (name) {
                case "trkseg":
                    readTrkSeg(parser);
                    break;
/*
                case "name":
                    courseName = readTrkName(parser);
                    break;
*/
                default:
                    skip(parser);
                    break;
            }
        }
    }

    private String readTrkName(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, "name");
        String txt = readText(parser);
        if (txt.length() > 15)
            txt = txt.substring(0, 15);
        parser.require(XmlPullParser.END_TAG, ns, "name");
        return txt;
    }

    private String readSimpleTextTag(XmlPullParser parser, String tagName) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, tagName);
        String txt = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, tagName);
        return txt;
    }

    private String readName(XmlPullParser parser) throws IOException, XmlPullParserException {
        return readSimpleTextTag(parser, "name");
    }

    private String readType(XmlPullParser parser) throws IOException, XmlPullParserException {
        return readSimpleTextTag(parser, "type");
    }

    private String readSymbol(XmlPullParser parser) throws IOException, XmlPullParserException {
        return readSimpleTextTag(parser, "sym");
    }

    private void readTrkSeg(XmlPullParser parser) throws XmlPullParserException, IOException, ParseException {
        parser.require(XmlPullParser.START_TAG, ns, "trkseg");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the entry tag
            if (name.equals("trkpt")) {
                readTrkPt(parser);
            } else {
                skip(parser);
            }
        }
    }

    private void readRte(XmlPullParser parser) throws XmlPullParserException, IOException, ParseException {
        parser.require(XmlPullParser.START_TAG, ns, "rte");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the entry tag
            switch (name) {
                case "rtept":
                    readRtePt(parser);
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
    }

    private void readTrkPt(XmlPullParser parser) throws XmlPullParserException, IOException, ParseException {
        parser.require(XmlPullParser.START_TAG, ns, "trkpt");
        Date time = null;
        String name = null;
        double lat = Double.parseDouble(parser.getAttributeValue(null, "lat"));
        double lon = Double.parseDouble(parser.getAttributeValue(null, "lon"));
        double ele = Double.NaN;

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            switch (tagName) {
                case "ele":
                    ele = readEle(parser);
                    break;
                case "time":
                    time = readTime(parser);
                    break;
                case "name":
                    name = readName(parser);
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        trkPoints.add(new WayPoint(name, lat, lon, ele, time, null, null));
    }

    private void readWpt(XmlPullParser parser) throws XmlPullParserException, IOException, ParseException {
        parser.require(XmlPullParser.START_TAG, ns, "wpt");
        String name = null;
        Date time = null;
        double lat = Double.parseDouble(parser.getAttributeValue(null, "lat"));
        double lon = Double.parseDouble(parser.getAttributeValue(null, "lon"));
        double ele = Double.NaN;
        String type = null;
        String symbol = null;

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            switch (tagName) {
                case "ele":
                    ele = readEle(parser);
                    break;
                case "time":
                    time = readTime(parser);
                    break;
                case "name":
                    name = readName(parser);
                    break;
                case "type":
                    type = readType(parser);
                    break;
                case "sym":
                    symbol = readSymbol(parser);
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        wayPoints.add(new WayPoint(name, lat, lon, ele, time, type, symbol));
    }

    private void readRtePt(XmlPullParser parser) throws XmlPullParserException, IOException, ParseException {
        parser.require(XmlPullParser.START_TAG, ns, "rtept");
        String name = null;
        Date time = null;
        double lat = Double.parseDouble(parser.getAttributeValue(null, "lat"));
        double lon = Double.parseDouble(parser.getAttributeValue(null, "lon"));
        double ele = Double.NaN;

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            switch (tagName) {
                case "ele":
                    ele = readEle(parser);
                    break;
                case "time":
                    time = readTime(parser);
                    break;
                case "name":
                    name = readName(parser);
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        rtePoints.add(new WayPoint(name, lat, lon, ele, time, null, null));
    }

    private double readEle(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, "ele");
        String txt = readText(parser);
        double ele = Double.parseDouble(txt);
        parser.require(XmlPullParser.END_TAG, ns, "ele");
        return ele;
    }

    private Date readTime(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, "time");
        String txt = readText(parser);

        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

        Date time;
        try {
            time = dateFormat.parse(txt);
        } catch (ParseException e) {
            time = null;
        } finally {
            parser.require(XmlPullParser.END_TAG, ns, "time");
        }
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

    /**
     * Grade adjusted pace based on a study by Alberto E. Minetti on the energy cost of
     * walking and running at extreme slopes.
     * <p>
     * see Minetti, A. E. et al. (2002). Energy cost of walking and running at extreme uphill and downhill slopes.
     * Journal of Applied Physiology 93, 1039-1046, http://jap.physiology.org/content/93/3/1039.full
     */
    public double getWalkingGradeFactor(double g) {
        return 1.0 + (g * (19.5 + g * (46.3 + g * (-43.3 + g * (-30.4 + g * 155.4))))) / 3.6;
    }

    public void writeFit(File outfile) {
        WayPoint last = null;
        double minEle = Double.NaN;
        double maxEle = Double.NaN;
        double totalAsc = Double.NaN;
        double totalDesc = Double.NaN;
        double dist = .0;
        double totaldist = .0;
        double cp_min_dist = .0;
        double lcdist = .0;
        double ldist = .0;
        double speed = mGpx2FitOptions.getSpeed();
        double minLat = 1000.0, minLong = 1000.0;
        double maxLat = -1000.0, maxLong = -1000.0;
        long duration = 0;
        boolean skipExtraCP = false;

        if (trkPoints.size() <= rtePoints.size()) {
            trkPoints.addAll(rtePoints);
            rtePoints.clear();
        }

        FileEncoder encode = new FileEncoder(outfile, Fit.ProtocolVersion.V2_0);

        {
            //Generate FileIdMessage
            FileIdMesg fileIdMesg = new FileIdMesg(); // Every FIT file MUST contain a 'File ID' message as the first message
            fileIdMesg.setManufacturer(Manufacturer.GARMIN);
            fileIdMesg.setType(com.garmin.fit.File.COURSE);
            fileIdMesg.setProduct(12345);
            fileIdMesg.setSerialNumber(12345L);
            fileIdMesg.setNumber(trkPoints.hashCode());
            fileIdMesg.setTimeCreated(new DateTime(new Date()));
            encode.write(fileIdMesg); // Encode the FileIDMesg
        }

        {
            CourseMesg courseMesg = new CourseMesg();
            courseMesg.setLocalNum(0);
            courseMesg.setName(getName());
            courseMesg.setSport(Sport.GENERIC);
            encode.write(courseMesg);
        }

        if (Log.isDebugEnabled())
            Log.debug("Track: {}", getName());
        WayPoint firstWayPoint = trkPoints.get(0);
        Date startDate = firstWayPoint.getTime();

        WayPoint lastWayPoint = trkPoints.get(trkPoints.size() - 1);

        boolean forceSpeed = mGpx2FitOptions.isForceSpeed();
        if (firstWayPoint.getTime().getTime() == lastWayPoint.getTime().getTime()) {
            if (!Double.isNaN(speed))
                forceSpeed = true;
        }
        Date endDate;

        if (forceSpeed) {
            endDate = startDate;
        } else {
            endDate = lastWayPoint.getTime();
        }

        for (WayPoint wpt : trkPoints) {
            double ele = wpt.getEle();
            if (!Double.isNaN(ele)) {
                if (minEle > ele || Double.isNaN(minEle))
                    minEle = ele;
                if (maxEle < ele || Double.isNaN(maxEle))
                    maxEle = ele;
            }

            minLat = Math.min(minLat, wpt.getLat());
            minLong = Math.min(minLong, wpt.getLon());
            maxLat = Math.max(maxLat, wpt.getLat());
            maxLong = Math.max(maxLong, wpt.getLon());

            double grade = .0;
            double gspeed = speed;
            if (last == null) {
                wpt.setTotaldist(.0);
            } else {
                double d = wpt.distance(last);

                if (mGpx2FitOptions.isUse3dDistance()) {
                    totaldist += wpt.distance3D(last);
                } else {
                    totaldist += d;
                }

                wpt.setTotaldist(totaldist);

                if ((!Double.isNaN(ele)) && (!Double.isNaN(last.getEle()))) {
                    double dele = ele - last.getEle();
                    if (dele > 0.0) {
                        if (Double.isNaN(totalAsc))
                            totalAsc = .0;
                        totalAsc += dele;
                    } else {
                        if (Double.isNaN(totalDesc))
                            totalDesc = .0;
                        totalDesc += Math.abs(dele);
                    }

                    if (mGpx2FitOptions.isWalkingGrade()) {
                        grade = dele / d;
                        gspeed = getWalkingGradeFactor(grade) * speed;
                    }
                }

                if (forceSpeed) {
                    endDate = new Date(endDate.getTime() + (long) (d / gspeed * 1000.0));
                    wpt.setTime(endDate);
                }
            }
            last = wpt;
/*
            if (Log.isDebugEnabled())
                Log.debug("{} [{} , {}] {} - {}", endDate, wpt.getLat(), wpt.getLon(), ele, wpt.getTotaldist());
            */
        }

        {
            LapMesg lapMesg = new LapMesg();
            lapMesg.setLocalNum(0);

            lapMesg.setTimestamp(new DateTime(startDate));
            lapMesg.setStartTime(new DateTime(startDate));

            lapMesg.setStartPositionLat(firstWayPoint.getLatSemi());
            lapMesg.setStartPositionLong(firstWayPoint.getLonSemi());

            lapMesg.setEndPositionLat(lastWayPoint.getLatSemi());
            lapMesg.setEndPositionLong(lastWayPoint.getLonSemi());

            if (Log.isDebugEnabled())
                Log.debug("Start: {} - End: {}", startDate.toString(), endDate.toString());

            duration = endDate.getTime() - startDate.getTime();

            lapMesg.setTotalTimerTime((float) (duration / 1000.0));
            lapMesg.setTotalDistance((float) totaldist);
            lapMesg.setAvgSpeed((float) (totaldist * 1000.0 / (double) duration));

            lapMesg.setTotalElapsedTime((float) (duration / 1000.0));

            if (!Double.isNaN(totalAsc)) {
                totalAsc += 0.5;
                if (Log.isDebugEnabled())
                    Log.debug("Total Ascent: {}", (int) totalAsc);
                lapMesg.setTotalAscent((int) totalAsc);
            }
            if (!Double.isNaN(totalDesc)) {
                totalDesc += 0.5;
                if (Log.isDebugEnabled())
                    Log.debug("Total Descent: {}", (int) totalDesc);
                lapMesg.setTotalDescent((int) totalDesc);
            }
            if (!Double.isNaN(maxEle)) {
                if (Log.isDebugEnabled())
                    Log.debug("Max. Elevation: {}", (int) maxEle);
                lapMesg.setMaxAltitude((float) maxEle);
            }

            if (!Double.isNaN(minEle)) {
                if (Log.isDebugEnabled())
                    Log.debug("Min. Elevation: {}", (int) minEle);

                lapMesg.setMinAltitude((float) minEle);
            }

            // Add the bounding box of the course in the undocumented fields
            try {
                Constructor c = Field.class.getDeclaredConstructor(String.class, int.class, int.class,
                        double.class, double.class, String.class,
                        boolean.class, Profile.Type.class);
                c.setAccessible(true);
                lapMesg.addField((Field) c.newInstance("bound_max_position_lat", 27, 133, 1.0D, 0.0D, "semicircles", false, Profile.Type.SINT32));
                lapMesg.addField((Field) c.newInstance("bound_max_position_long", 28, 133, 1.0D, 0.0D, "semicircles", false, Profile.Type.SINT32));
                lapMesg.addField((Field) c.newInstance("bound_min_position_lat", 29, 133, 1.0D, 0.0D, "semicircles", false, Profile.Type.SINT32));
                lapMesg.addField((Field) c.newInstance("bound_min_position_long", 30, 133, 1.0D, 0.0D, "semicircles", false, Profile.Type.SINT32));
                lapMesg.setFieldValue(27, 0, (Integer) WayPoint.toSemiCircles(maxLat), '\uffff');
                lapMesg.setFieldValue(28, 0, (Integer) WayPoint.toSemiCircles(maxLong), '\uffff');
                lapMesg.setFieldValue(29, 0, (Integer) WayPoint.toSemiCircles(minLat), '\uffff');
                lapMesg.setFieldValue(30, 0, (Integer) WayPoint.toSemiCircles(minLong), '\uffff');
            } catch (NoSuchMethodException e) {
                ;
            } catch (IllegalAccessException e) {
                ;
            } catch (InstantiationException e) {
                ;
            } catch (InvocationTargetException e) {
                ;
            }

            encode.write(lapMesg);
        }

        cp_min_dist = totaldist / 48.0;
        if (cp_min_dist < mGpx2FitOptions.getMinCoursePointDistance())
            cp_min_dist = mGpx2FitOptions.getMinCoursePointDistance();

        double pt_min_dist = 0;
        if (mGpx2FitOptions.getMaxPoints() != 0) {
            pt_min_dist = totaldist / mGpx2FitOptions.getMaxPoints();
            if ((trkPoints.size() + rtePoints.size() + wayPoints.size()) > mGpx2FitOptions.getMaxPoints()) {
                skipExtraCP = true;
            }
        }
        if (pt_min_dist < mGpx2FitOptions.getMinRoutePointDistance())
            pt_min_dist = mGpx2FitOptions.getMinRoutePointDistance();


        if (!skipExtraCP && !wayPoints.isEmpty()) {
            writeWayPoints(encode, wayPoints);
        }

        if (!skipExtraCP && !rtePoints.isEmpty()) {
            writeWayPoints(encode, rtePoints);
        }

        {
            EventMesg eventMesg = new EventMesg();
            eventMesg.setLocalNum(0);

            eventMesg.setEvent(Event.TIMER);
            eventMesg.setEventType(EventType.START);
            eventMesg.setEventGroup((short) 0);
            eventMesg.setTimestamp(new DateTime(startDate));
            encode.write(eventMesg);
        }

        DateTime timestamp = new DateTime(new Date(WayPoint.RefMilliSec));
        long ltimestamp = startDate.getTime();

        long i = 0;
        last = null;

        if (mGpx2FitOptions.isInjectCoursePoints()) {
            for (WayPoint wpt : trkPoints) {
                CoursePointMesg cp = new CoursePointMesg();
                cp.setLocalNum(0);

                boolean written = false;
                i += 1;

                if (duration != 0)
                    timestamp = new DateTime(wpt.getTime());
                else
                    timestamp = new DateTime(new Date(WayPoint.RefMilliSec + i * 1000));

                double gspeed = Double.NaN;
                dist = wpt.getTotaldist();

                if (last == null) {
                    cp.setPositionLat(wpt.getLatSemi());
                    cp.setPositionLong(wpt.getLonSemi());
                    cp.setName("Start");
                    cp.setType(CoursePoint.GENERIC);

                    cp.setDistance((float) dist);
                    cp.setTimestamp(timestamp);
                    encode.write(cp);
                    written = true;
                }

                if (wpt.equals(lastWayPoint)) {
                    cp.setPositionLat(wpt.getLatSemi());
                    cp.setPositionLong(wpt.getLonSemi());
                    cp.setName("End");
                    cp.setType(CoursePoint.GENERIC);
                    cp.setDistance((float) dist);
                    cp.setTimestamp(timestamp);
                    encode.write(cp);
                    written = true;
                } else if ((dist - lcdist) > cp_min_dist) {
                    cp.setName("");
                    cp.setType(CoursePoint.GENERIC);
                    cp.setPositionLat(wpt.getLatSemi());
                    cp.setPositionLong(wpt.getLonSemi());
                    cp.setDistance((float) dist);
                    cp.setTimestamp(timestamp);
                    encode.write(cp);
                    lcdist = dist;
                    written = true;
                }

                last = wpt;
                if (Log.isDebugEnabled()) {
                    if (written) {
                        Log.debug("{} [{} , {}] {} - {} - {}", timestamp.toString(),
                                wpt.getLat(), wpt.getLon(), wpt.getEle(), dist, gspeed);
                    }
                }
            }

            i = 0;
            last = null;

        }

        for (WayPoint wpt : trkPoints) {
            boolean written = false;
            i += 1;

            if (duration != 0)
                timestamp = new DateTime(wpt.getTime());
            else
                timestamp = new DateTime(new Date(WayPoint.RefMilliSec + i * 1000));

            double gspeed = Double.NaN;
            dist = wpt.getTotaldist();

            if ((last == null) || (dist - ldist) > pt_min_dist) {
                RecordMesg r = new RecordMesg();
                r.setLocalNum(0);

                r.setPositionLat(wpt.getLatSemi());
                r.setPositionLong(wpt.getLonSemi());
                r.setDistance((float) dist);
                r.setTimestamp(timestamp);

                if (!Double.isNaN(wpt.getEle()))
                    r.setAltitude((float) wpt.getEle());

                long l = timestamp.getDate().getTime();

                if (ltimestamp != l) {
                    gspeed = (dist - ldist) / (l - ltimestamp) * 1000.0;
                    r.setSpeed((float) gspeed);
                } else {
                    r.setSpeed((float) 0.0);
                }

                encode.write(r);
                ldist = dist;
                ltimestamp = l;
                written = true;
            }

            last = wpt;
            if (Log.isDebugEnabled()) {
                if (written) {
                    Log.debug("{} [{} , {}] {} - {} - {}", timestamp.toString(),
                            wpt.getLat(), wpt.getLon(), wpt.getEle(), dist, gspeed);
                }
            }
        }

        {
            EventMesg eventMesg = new EventMesg();
            eventMesg.setLocalNum(0);

            eventMesg.setEvent(Event.TIMER);
            eventMesg.setEventType(EventType.STOP_DISABLE_ALL);
            eventMesg.setEventGroup((short) 0);
            //timestamp.add(2);
            eventMesg.setTimestamp(timestamp);

            encode.write(eventMesg);
        }

        encode.close();
    }

    private void writeWayPoints(FileEncoder encode, List<WayPoint> points) {
        for (WayPoint wpt : points) {
            CoursePointMesg cp = new CoursePointMesg();
            cp.setLocalNum(0);

            cp.setPositionLat(wpt.getLatSemi());
            cp.setPositionLong(wpt.getLonSemi());
            String name = wpt.getName();
            if (name != null) {
                cp.setName(name);
            } else {
                cp.setName("");
            }
            cp.setType(wpt.getPointType());
            encode.write(cp);
        }
    }
}
