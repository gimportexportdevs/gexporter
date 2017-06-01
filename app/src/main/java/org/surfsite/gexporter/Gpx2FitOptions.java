package org.surfsite.gexporter;


/**
 * Created by harald on 27.05.17.
 */

public class Gpx2FitOptions {
    private double speed;
    private boolean use3dDistance;
    private boolean forceSpeed;
    private boolean injectCoursePoints;
    private boolean walkingGrade;
    private double minRoutePointDistance;
    private double minCoursePointDistance;
    private int maxPoints;
    private int speedUnit;

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
