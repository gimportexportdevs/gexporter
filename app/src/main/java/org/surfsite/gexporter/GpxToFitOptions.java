package org.surfsite.gexporter;

/**
 * Created by harald on 27.05.17.
 */

public class GpxToFitOptions {
    private double speed;
    private boolean use3dDistance;
    private boolean forceSpeed;
    private boolean injectCoursePoints;
    private double minRoutePointDistance;
    private double minCoursePointDistance;

    public GpxToFitOptions() {
        speed = Double.NaN;
        use3dDistance = true;
        forceSpeed = false;
        injectCoursePoints = false;
        minRoutePointDistance = 5.0;
        minCoursePointDistance = 1000.0;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public boolean isUse3dDistance() {
        return use3dDistance;
    }

    public void setUse3dDistance(boolean use3dDistance) {
        this.use3dDistance = use3dDistance;
    }

    public boolean isForceSpeed() {
        return forceSpeed;
    }

    public void setForceSpeed(boolean forceSpeed) {
        this.forceSpeed = forceSpeed;
    }

    public boolean isInjectCoursePoints() {
        return injectCoursePoints;
    }

    public void setInjectCoursePoints(boolean injectCoursePoints) {
        this.injectCoursePoints = injectCoursePoints;
    }

    public double getMinRoutePointDistance() {
        return minRoutePointDistance;
    }

    public void setMinRoutePointDistance(double minRoutePointDistance) {
        this.minRoutePointDistance = minRoutePointDistance;
    }

    public double getMinCoursePointDistance() {
        return minCoursePointDistance;
    }

    public void setMinCoursePointDistance(double minCoursePointDistance) {
        this.minCoursePointDistance = minCoursePointDistance;
    }
}
