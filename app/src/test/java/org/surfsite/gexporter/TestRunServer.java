package org.surfsite.gexporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class TestRunServer {
    private static Logger Log = LoggerFactory.getLogger(TestRunServer.class);

    static public void main(String [] args) {
        WebServer server = null;
        try {
            String homedir = System.getProperty("user.home") + "/Downloads/";
            Log.debug("Serving from homedir {}", homedir);

            server = new WebServer(new File(homedir),
                    new File("/tmp"), 22222, new Gpx2FitOptions());
            server.start();
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            System.exit(1);
        }
        while (true) {
            try {
                Thread.sleep(1000 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

