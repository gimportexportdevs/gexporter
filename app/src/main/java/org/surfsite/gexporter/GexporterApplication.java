package org.surfsite.gexporter;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public class GexporterApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Material You: take the colors from the wallpaper on Android 12+
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
