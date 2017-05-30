package org.surfsite.gexporter;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

/**
 * Created by harald on 27.05.17.
 */

public class GpxToFitOptions {
    private double speed;
    private boolean use3dDistance;
    private boolean forceSpeed;
    private boolean injectCoursePoints;
    private boolean walkingGrade;
    private double minRoutePointDistance;
    private double minCoursePointDistance;
    private int maxPoints;
    private int speedUnit;

    public GpxToFitOptions() {
        speed = Double.NaN;
        use3dDistance = true;
        walkingGrade = false;
        forceSpeed = false;
        injectCoursePoints = false;
        minRoutePointDistance = .0;
        minCoursePointDistance = .0;
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

    public void save(Application app) {
        SharedPreferences mPrefs=app.getSharedPreferences(app.getApplicationInfo().name, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed=mPrefs.edit();
        Gson gson = new Gson();
        ed.putString(this.getClass().getName(), gson.toJson(this));
        ed.apply();
    }

    static public GpxToFitOptions load(Application app) {
        Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
        JsonParser parser=new JsonParser();
        SharedPreferences mPrefs=app.getSharedPreferences(app.getApplicationInfo().name, Context.MODE_PRIVATE);
        String json = mPrefs.getString(GpxToFitOptions.class.getName(), null);
        GpxToFitOptions opts = null;
        if (json != null && json.length() > 0)
            opts = gson.fromJson(parser.parse(json).getAsJsonObject(), GpxToFitOptions.class);
        if (opts != null)
            return opts;
        else
            return new GpxToFitOptions();
    }
}
