package org.tracks.exporter;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private WebServer server;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        server = new WebServer(22222);
        try {
            server.start();
            Log.w("Httpd", "Web server initialized.");
        } catch(IOException ioe) {
            Log.w("Httpd", "The server could not start. " + ioe.getLocalizedMessage());
        }
    }

    // DON'T FORGET to stop the server
    @Override
    public void onDestroy()
    {
        super.onDestroy();
        if (server != null)
            server.stop();
    }
}
