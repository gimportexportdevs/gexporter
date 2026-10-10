package org.surfsite.gexporter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class TestFileTypes {

    private static byte[] head(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String sniff(byte[] b) {
        return FileTypes.sniffExtension(b, b.length);
    }

    @Test
    public void trackFileNames() {
        assertTrue(FileTypes.isTrackFile("tour.gpx"));
        assertTrue(FileTypes.isTrackFile("tour.FIT"));
        assertFalse(FileTypes.isTrackFile("tour.txt"));
        assertFalse(FileTypes.isTrackFile(null));
    }

    @Test
    public void sniffsGpx() {
        assertEquals(".gpx", sniff(head("<?xml version=\"1.0\"?><gpx>")));
        assertEquals(".gpx", sniff(head("﻿<?xml version=\"1.0\"?>")));
        assertEquals(".gpx", sniff(head("\n  <gpx version=\"1.1\">")));
    }

    @Test
    public void sniffsFit() {
        byte[] fit = new byte[14];
        fit[0] = 14;
        fit[8] = '.';
        fit[9] = 'F';
        fit[10] = 'I';
        fit[11] = 'T';
        assertEquals(".fit", sniff(fit));
    }

    @Test
    public void sniffsRealFixture() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("sample11.gpx")) {
            byte[] buf = new byte[1024];
            int len = in.read(buf);
            assertEquals(".gpx", FileTypes.sniffExtension(buf, len));
        }
    }

    @Test
    public void rejectsOtherContent() {
        assertNull(sniff(head("Just some shared text")));
        assertNull(sniff(head("%PDF-1.7")));
        assertNull(sniff(new byte[0]));
    }

    @Test
    public void safeFileNameStripsPaths() {
        assertEquals("x.xml", FileTypes.safeFileName("../../shared_prefs/x.xml"));
        assertEquals("tour.gpx", FileTypes.safeFileName("/storage/emulated/0/tour.gpx"));
        assertEquals("tour.gpx", FileTypes.safeFileName("..\\..\\tour.gpx"));
        assertEquals(FileTypes.FALLBACK_NAME, FileTypes.safeFileName(".."));
        assertEquals(FileTypes.FALLBACK_NAME, FileTypes.safeFileName("  "));
        assertEquals(FileTypes.FALLBACK_NAME, FileTypes.safeFileName(null));
    }
}
