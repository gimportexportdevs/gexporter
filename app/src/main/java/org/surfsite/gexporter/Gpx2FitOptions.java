package org.surfsite.gexporter;


/**
 * Created by harald on 27.05.17.
 */

public class Gpx2FitOptions {
    // volatile: written on the UI thread, read on NanoHTTPD request threads.
    private volatile double speed;
    private volatile boolean use3dDistance;
    private volatile boolean forceSpeed;
    private volatile boolean injectCoursePoints;
    private volatile boolean walkingGrade;
    private volatile double minRoutePointDistance;
    private volatile double minCoursePointDistance;
    private volatile int maxPoints;
    private volatile int speedUnit;

    /**
     * Copy all settings from another instance into this one. Lets a single
     * long-lived options object (the one a running WebServer holds) be updated
     * in place, instead of the server keeping a stale reference.
     */
    public void copyFrom(Gpx2FitOptions o) {
        this.speed = o.speed;
        this.use3dDistance = o.use3dDistance;
        this.forceSpeed = o.forceSpeed;
        this.injectCoursePoints = o.injectCoursePoints;
        this.walkingGrade = o.walkingGrade;
        this.minRoutePointDistance = o.minRoutePointDistance;
        this.minCoursePointDistance = o.minCoursePointDistance;
        this.maxPoints = o.maxPoints;
        this.speedUnit = o.speedUnit;
    }

    public Gpx2FitOptions() {
        speed = 1000.0 / 14.0 / 60.0;
        use3dDistance = true;
        walkingGrade = false;
        forceSpeed = false;
        injectCoursePoints = false;
        minRoutePointDistance = 1.0;
        minCoursePointDistance = 1000.0;
        maxPoints = 1000;
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

    public boolean isWalkingGrade() {
        return walkingGrade;
    }

    public void setWalkingGrade(boolean walkingGrade) {
        this.walkingGrade = walkingGrade;
    }

    public int getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(int maxPoints) {
        this.maxPoints = maxPoints;
    }

    public int getSpeedUnit() {
        return speedUnit;
    }

    public void setSpeedUnit(int speedUnit) {
        this.speedUnit = speedUnit;
    }
}
