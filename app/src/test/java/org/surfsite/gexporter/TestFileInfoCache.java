package org.surfsite.gexporter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** dir.json reads a file's fingerprint and locations once, and again after a change. */
public class TestFileInfoCache {
    private File dir;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("served").toFile();
    }

    @After
    public void tearDown() {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
        dir.delete();
    }

    @Test
    public void unchangedFileIsNotReadAgain() throws Exception {
        File track = new File(dir, "track.gpx");
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("sample11.gpx")) {
            Files.copy(in, track.toPath());
        }
        FileInfoCache.Info first = FileInfoCache.get(track);
        assertNull(first.locations);
        assertEquals(WatchContent.fingerprint(track), first.fingerprint);
        assertSame(first, FileInfoCache.get(track));
    }

    @Test
    public void changedFileIsReadAgain() throws Exception {
        File file = new File(dir, "place.gpx");
        Files.write(file.toPath(), new SharedLocation("Stephansdom", 48.2084, 16.3731).toGpx()
                .getBytes(StandardCharsets.UTF_8));
        FileInfoCache.Info before = FileInfoCache.get(file);
        assertNotNull(before.locations);

        try (InputStream in = getClass().getClassLoader().getResourceAsStream("sample11.gpx")) {
            Files.copy(in, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        FileInfoCache.Info after = FileInfoCache.get(file);
        assertNull(after.locations);
        assertNotEquals(before.fingerprint, after.fingerprint);
    }

    @Test
    public void warmFillsTheCacheAndForgetsRemovedFiles() throws Exception {
        File kept = new File(dir, "kept.gpx");
        File removed = new File(dir, "removed.gpx");
        for (File f : new File[]{kept, removed}) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("sample11.gpx")) {
                Files.copy(in, f.toPath());
            }
        }
        FileInfoCache.warm(dir);
        assertTrue(FileInfoCache.contains(kept));
        assertTrue(FileInfoCache.contains(removed));

        assertTrue(removed.delete());
        FileInfoCache.warm(dir);
        assertTrue(FileInfoCache.contains(kept));
        assertFalse(FileInfoCache.contains(removed));
    }
}
