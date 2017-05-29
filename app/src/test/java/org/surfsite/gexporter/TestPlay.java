package org.surfsite.gexporter;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestPlay {

    @Test
    public void test10() {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("sample10.gpx").getFile());
        try {
            Gpx2Fit loader = new Gpx2Fit(file, new GpxToFitOptions());
            System.out.println(String.format("Track: %s", loader.getName()));
            assertEquals("sample10", loader.getName());
            for (WayPoint wpt : loader.getWaypoints()) {
                System.out.println(String.format("[%s , %s] %s", wpt.getLat(), wpt.getLon(), wpt.getEle()));
            }
        } catch (Exception e) {
            fail(e.toString());
        }

    }

    @Test
    public void test11() {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("sample11.gpx").getFile());
        try {
            Gpx2Fit loader = new Gpx2Fit(file, new GpxToFitOptions());
            System.out.println(String.format("Track: %s", loader.getName()));
            assertEquals("sample11", loader.getName());

            for (WayPoint wpt : loader.getWaypoints()) {
                System.out.println(String.format("[%s , %s] %s", wpt.getLat(), wpt.getLon(), wpt.getEle()));
            }
        } catch (Exception e) {
            fail(e.toString());
        }

    }

    @Test
    public void testFit() {
        testFit("sample10.gpx", "sample10.fit");
        testFit("sample11.gpx", "sample11.fit");
        testFit("sample11-2.gpx", "sample11-2.fit");
        testFit("sample11-3.gpx", "sample11-3.fit");
        testFit("sample11-route.gpx", "sample11-route.fit");
    }

    void testFit(String inFileName, String outFileName) {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(inFileName).getFile());

        Gpx2Fit loader;
        try {
            GpxToFitOptions options = new GpxToFitOptions();
            loader = new Gpx2Fit(file, options);
            options.setSpeed(1000.0 / (14.0 * 60.0) );
            options.setInjectCoursePoints(false);
            loader.writeFit(new File(outFileName));
        } catch (Exception e) {
            fail(e.toString());
        }
    }
}
