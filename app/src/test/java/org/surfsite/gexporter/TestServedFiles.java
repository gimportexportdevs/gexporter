package org.surfsite.gexporter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class TestServedFiles {

    @Test
    public void listsTracksLocationsAndFitFiles() throws Exception {
        File dir = Files.createTempDirectory("served").toFile();
        try {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("sample11.gpx")) {
                Files.copy(in, new File(dir, "b-track.gpx").toPath());
            }
            try (OutputStream out = new FileOutputStream(new File(dir, "a-place.gpx"))) {
                out.write(new SharedLocation("Stephansdom", 48.2084, 16.3731).toGpx()
                        .getBytes(StandardCharsets.UTF_8));
            }
            new File(dir, "c-course.fit").createNewFile();
            new File(dir, "notes.txt").createNewFile();

            List<ServedFiles.Entry> entries = ServedFiles.scan(dir);
            assertEquals(3, entries.size());

            assertEquals("Stephansdom", entries.get(0).name);
            assertTrue(entries.get(0).location);

            assertEquals("b-track", entries.get(1).name);
            assertFalse(entries.get(1).location);
            assertTrue("track has a length", entries.get(1).length > 0);

            assertEquals("c-course", entries.get(2).name);
            assertEquals(ServedFiles.NO_LENGTH, entries.get(2).length, 0);
        } finally {
            for (File f : dir.listFiles()) {
                f.delete();
            }
            dir.delete();
        }
    }

    @Test
    public void missingDirectoryIsEmpty() {
        assertTrue(ServedFiles.scan(new File("/does/not/exist")).isEmpty());
    }
}
