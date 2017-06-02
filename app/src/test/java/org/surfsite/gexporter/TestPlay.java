package org.surfsite.gexporter;

import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestPlay {

    @Test
    public void test10() {
        ClassLoader classLoader = getClass().getClassLoader();
        String filename = "sample10.gpx";
        File file = new File(classLoader.getResource(filename).getFile());
        try {
            Gpx2Fit loader = new Gpx2Fit(WebServer.getCourseName(filename), file, new Gpx2FitOptions());
            System.out.println(String.format("Track: %s", loader.getName()));
            assertEquals("sample10", loader.getName());
            List<WayPoint> wpts = loader.getWaypoints();
            assertEquals(wpts.size(), 35);
        } catch (Exception e) {
            fail(e.toString());
        }
    }

    @Test
    public void test11() {
        ClassLoader classLoader = getClass().getClassLoader();
        String filename = "sample11.gpx";
        File file = new File(classLoader.getResource(filename).getFile());
        try {
            Gpx2Fit loader = new Gpx2Fit(WebServer.getCourseName(filename), file, new Gpx2FitOptions());
            System.out.println(String.format("Track: %s", loader.getName()));
            assertEquals("sample11", loader.getName());
            List<WayPoint> wpts = loader.getWaypoints();
            assertEquals(wpts.size(), 339);
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
        testFit("sample2.gpx", "sample2.fit");
    }

    private void testFit(String inFileName, String outFileName) {

        ClassLoader classLoader = getClass().getClassLoader();
        URL resource = classLoader.getResource(inFileName);
        if (resource == null) throw new AssertionError();
        File file = new File(resource.getFile());

        Gpx2Fit loader;
        try {
            Gpx2FitOptions options = new Gpx2FitOptions();
            loader = new Gpx2Fit(WebServer.getCourseName(inFileName), file, options);
            loader.writeFit(new File(outFileName));
            options.setSpeed(1000.0 / (13.0 * 60.0) );
            options.setMaxPoints(1000);
            options.setInjectCoursePoints(true);
            options.setForceSpeed(true);
            options.setWalkingGrade(true);
            options.setMinRoutePointDistance(5.0);
            options.setMinCoursePointDistance(1000.0);
            loader.writeFit(new File(outFileName));
        } catch (Exception e) {
            fail(e.toString());
        }
    }
}
