package org.surfsite.gexporter;

import static org.apache.commons.lang3.StringUtils.isNumeric;
import static org.apache.commons.lang3.math.NumberUtils.toShort;

import com.garmin.fit.CoursePoint;

import org.gavaghan.geodesy.Ellipsoid;
import org.gavaghan.geodesy.GeodeticCalculator;
import org.gavaghan.geodesy.GlobalCoordinates;

import java.util.Date;

public class WayPoint {
    // instantiate the calculator
    private static final GeodeticCalculator geoCalc = new GeodeticCalculator();

    // milliseconds since UTC 00:00 Dec 31 1989"
    public static final long RefMilliSec = 631065600000L;
    public static final Date RefDate = new Date(RefMilliSec);

    // select a reference ellipsoid
    private static final Ellipsoid reference = Ellipsoid.WGS84;

    private double lat;
    private double lon;
    private double ele;
    private Date time;
    private double totaldist = Double.NaN;
    private String name;

    private String type;
    private String symbol;

    public WayPoint(String name, double lat, double lon, double ele, Date time, String type, String symbol) {
        this.lat = lat;
        this.lon = lon;
        this.ele = ele;
        this.time = time;
        this.name = name;
        if (this.time == null) {
            this.time = RefDate;
        }
        this.type = type;
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public int getLatSemi() {
        return toSemiCircles(lat);
    }

    public int getLonSemi() {
        return toSemiCircles(lon);
    }

    public static int toSemiCircles(double i) {
        double d = i * 2147483648.0 / 180.0;
        return (int) d;
    }

    public double getEle() {
        return ele;
    }

    public Date getTime() {
        return time;
    }

    public String getType() {
        return type;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public void setEle(double ele) {
        this.ele = ele;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public double distance(WayPoint other) {
        return geoCalc.calculateGeodeticCurve(reference,
                new GlobalCoordinates(getLat(), this.getLon()),
                new GlobalCoordinates(other.getLat(), other.getLon())
        ).getEllipsoidalDistance();
    }

    public double distance3D(WayPoint other) {
        double d = geoCalc.calculateGeodeticCurve(reference,
                new GlobalCoordinates(getLat(), this.getLon()),
                new GlobalCoordinates(other.getLat(), other.getLon())
        ).getEllipsoidalDistance();

        if (!Double.isNaN(getEle()) && !Double.isNaN(other.getEle())) {
            double h = (getEle() - other.getEle());
            return Math.sqrt(d * d + h * h);
        } else return d;
    }

    public double getTotaldist() {
        return totaldist;
    }

    public void setTotaldist(double totaldist) {
        this.totaldist = totaldist;
    }

    public CoursePoint getPointType() {
        if (parseStringToCoursePoint(type) != null) {
            return parseStringToCoursePoint(type);
        }
        if (parseStringToCoursePoint(symbol) != null) {
            return parseStringToCoursePoint(symbol);
        }
        return CoursePoint.GENERIC;
    }

    private CoursePoint parseStringToCoursePoint(String s) {
        if (isNumeric(s)) {
            return CoursePoint.getByValue(toShort(s));
        } else {
            try {
                return CoursePoint.valueOf(s);
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }
}
