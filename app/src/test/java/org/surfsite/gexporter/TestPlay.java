package org.surfsite.gexporter;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestPlay {

    @Test
    public void test10() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        String filename = "sample10.gpx";
        File file = new File(classLoader.getResource(filename).getFile());
        Gpx2Fit loader = new Gpx2Fit(WebServer.getCourseName(filename), new FileInputStream(file), new Gpx2FitOptions());
        System.out.println(String.format("Track: %s", loader.getName()));
        assertEquals("sample10", loader.getName());
        List<WayPoint> wpts = loader.getWaypoints();
        assertEquals(35, wpts.size());
    }

    @Test
    public void test11() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        String filename = "sample11.gpx";
        File file = new File(classLoader.getResource(filename).getFile());
        Gpx2Fit loader = new Gpx2Fit(WebServer.getCourseName(filename), new FileInputStream(file), new Gpx2FitOptions());
        System.out.println(String.format("Track: %s", loader.getName()));
        assertEquals("sample11", loader.getName());
        List<WayPoint> wpts = loader.getWaypoints();
        assertEquals(339, wpts.size());
    }

    @Test
    public void test11_route_reduce() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        String filename = "sample11-route.gpx";
        File file = new File(classLoader.getResource(filename).getFile());

        Gpx2FitOptions options = new Gpx2FitOptions();
        options.setMaxPoints(100);

        Gpx2Fit loader = new Gpx2Fit(WebServer.getCourseName(filename), new FileInputStream(file), options);

        System.out.println(String.format("Track: %s", loader.getName()));
        assertEquals("sample11-route", loader.getName());
        List<WayPoint> wpts = loader.getWaypoints();
        assertTrue(String.format("Waypoint size %d not < 100", wpts.size()), wpts.size() < 100);
    }

    @Test
    public void testFit_sample_10() throws Exception {
        testFit("sample10.gpx", "sample10.fit");
    }

    @Test
    public void testFit_sample_11() throws Exception {
        testFit("sample11.gpx", "sample11.fit");
    }

    @Test
    public void testFit_sample_11_2() throws Exception {
        testFit("sample11-2.gpx", "sample11-2.fit");
    }

    @Test
    public void testFit_sample_11_3() throws Exception {
        testFit("sample11-3.gpx", "sample11-3.fit");
    }

    @Test
    public void testFit_sample_11_route() throws Exception {
        testFit("sample11-route.gpx", "sample11-route.fit");
    }

    @Test
    public void testFit_sample_2() throws Exception {
        testFit("sample2.gpx", "sample2.fit");
    }

    private void testFit(String inFileName, String outFileName) throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        URL resource = classLoader.getResource(inFileName);
        if (resource == null) throw new AssertionError();
        File file = new File(resource.getFile());

        Gpx2Fit loader;
        Gpx2FitOptions options = new Gpx2FitOptions();
        loader = new Gpx2Fit(WebServer.getCourseName(inFileName), new FileInputStream(file), options);
        loader.writeFit(new File(outFileName));
        options.setSpeed(1000.0 / (13.0 * 60.0));
        options.setMaxPoints(1000);
        options.setInjectCoursePoints(true);
        options.setForceSpeed(true);
        options.setWalkingGrade(true);
        options.setMinRoutePointDistance(5.0);
        options.setMinCoursePointDistance(1000.0);
        loader.writeFit(new File(outFileName));
    }
}
