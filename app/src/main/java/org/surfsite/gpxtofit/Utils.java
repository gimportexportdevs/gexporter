package org.surfsite.gpxtofit;

import org.gavaghan.geodesy.*;

public class Utils {


    public static double fromSemiCircles(int i) {
        return i * 180.0 / 2147483648.0;
    }

    public static int toSemiCircles(double i) {
        double d = i * 2147483648.0 / 180.0;
        return (int) d;
    }

    public static double degreeToRadian(double degree) {
        return (Math.PI / 180.0) * degree;
    }

}
