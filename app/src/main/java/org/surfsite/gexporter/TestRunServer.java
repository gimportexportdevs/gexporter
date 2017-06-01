package org.surfsite.gexporter;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

/**
 * Created by harald on 01.06.17.
 */

public class TestRunServer {
    public static void main(String [] args)
    {
        WebServer server = null;
        try {
            server = new WebServer(new File("/home/harald/Downloads/"),
                    new File("/tmp"), 22222, new GpxToFitOptions());
            server.start();
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        while (true) {
            try {
                Thread.sleep(1000*1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

