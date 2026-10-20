package org.surfsite.gexporter;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

/** Persists the GPX to FIT conversion options (shared by MainActivity and SettingsActivity). */
final class OptionsStore {

    private OptionsStore() {
    }

    private static SharedPreferences prefs(Context context) {
        Context app = context.getApplicationContext();
        return app.getSharedPreferences(app.getApplicationInfo().name, Context.MODE_PRIVATE);
    }

    static void save(Context context, Gpx2FitOptions options) {
        prefs(context).edit()
                .putString(Gpx2FitOptions.class.getName(), new Gson().toJson(options))
                .apply();
    }

    static Gpx2FitOptions load(Context context) {
        Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
        String json = prefs(context).getString(Gpx2FitOptions.class.getName(), null);
        Gpx2FitOptions opts = null;
        if (json != null && !json.isEmpty()) {
            opts = gson.fromJson(JsonParser.parseString(json).getAsJsonObject(), Gpx2FitOptions.class);
        }
        return opts != null ? opts : new Gpx2FitOptions();
    }
}
