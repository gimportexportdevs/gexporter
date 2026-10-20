package org.surfsite.gexporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** What the main screen lists as ready for the watch: tracks and shared locations. */
final class ServedFiles {
    private static final Logger Log = LoggerFactory.getLogger(ServedFiles.class);

    /** Length is unknown, e.g. for FIT files. */
    static final double NO_LENGTH = -1;

    static final class Entry {
        final File file;
        final String name;
        final boolean location;
        /** meters, or {@link #NO_LENGTH} */
        final double length;

        Entry(File file, String name, boolean location, double length) {
            this.file = file;
            this.name = name;
            this.location = location;
            this.length = length;
        }
    }

    private ServedFiles() {
    }

    /**
     * Read the directory. Parses every GPX file, so call it off the UI
     * thread. A GPX file holding only waypoints yields one location entry
     * per waypoint; unreadable files are listed as tracks without length.
     */
    static List<Entry> scan(File dir) {
        List<Entry> entries = new ArrayList<>();
        String[] names = dir.list((d, name) -> FileTypes.isTrackFile(name));
        if (names == null) {
            return entries;
        }
        Arrays.sort(names);
        for (String name : names) {
            File file = new File(dir, name);
            String title = withoutExtension(name);
            if (!(name.endsWith(".gpx") || name.endsWith(".GPX"))) {
                entries.add(new Entry(file, title, false, NO_LENGTH));
                continue;
            }
            try (FileInputStream in = new FileInputStream(file)) {
                Gpx2Fit gpx = new Gpx2Fit(name, in, new Gpx2FitOptions());
                if (!gpx.hasTrackOrRoute() && !gpx.getWptPoints().isEmpty()) {
                    for (WayPoint wpt : gpx.getWptPoints()) {
                        String wptName = wpt.getName();
                        entries.add(new Entry(file,
                                wptName == null || wptName.trim().isEmpty() ? title : wptName.trim(),
                                true, NO_LENGTH));
                    }
                } else {
                    entries.add(new Entry(file, title, false, gpx.getLength()));
                }
            } catch (Exception e) {
                Log.warn("Could not read {}: {}", name, e.toString());
                entries.add(new Entry(file, title, false, NO_LENGTH));
            }
        }
        return entries;
    }

    static String withoutExtension(String name) {
        return FileTypes.isTrackFile(name) ? name.substring(0, name.length() - 4) : name;
    }
}
