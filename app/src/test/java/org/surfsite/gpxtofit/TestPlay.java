package org.surfsite.gpxtofit;

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
            GPXLoader loader = new GPXLoader(file);
            System.out.println(String.format("Track: %s", loader.getName()));
            assertEquals("67329", loader.getName());
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
            GPXLoader loader = new GPXLoader(file);
            System.out.println(String.format("Track: %s", loader.getName()));
            assertEquals("51-Quer-durch-d", loader.getName());

            for (WayPoint wpt : loader.getWaypoints()) {
                System.out.println(String.format("[%s , %s] %s", wpt.getLat(), wpt.getLon(), wpt.getEle()));
            }
        } catch (Exception e) {
            fail(e.toString());
        }

    }

    @Test
    public void testFit() {
        //testFit("sample10.gpx", "/dev/null");
        //testFit("sample11.gpx", "/dev/null");
        testFit("sample11-2.gpx", "example.fit");
        //testFit("sample11-3.gpx", "example.fit");
    }

    void testFit(String inFileName, String outFileName) {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(inFileName).getFile());

        GPXLoader loader;
        try {
            loader = new GPXLoader(file);
            loader.writeFit(new File(outFileName));
        } catch (Exception e) {
            fail(e.toString());
        }
    }
}
