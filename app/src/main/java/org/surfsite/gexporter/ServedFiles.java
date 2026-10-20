package org.surfsite.gexporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
        /** coordinates of a location; NaN for tracks */
        final double lat;
        final double lon;
        /** identifies the content on the watch, see WatchContent.fingerprint() */
        final String fingerprint;

        Entry(File file, String name, boolean location, double length) {
            this(file, name, location, length, Double.NaN, Double.NaN);
        }

        Entry(File file, String name, boolean location, double length, double lat, double lon) {
            this.file = file;
            this.name = name;
            this.location = location;
            this.length = length;
            this.lat = lat;
            this.lon = lon;
            this.fingerprint = location ? WatchContent.fingerprint(name, lat, lon) : WatchContent.fingerprint(file);
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
            List<WayPoint> locations = locationsIn(file);
            if (locations != null) {
                for (WayPoint wpt : locations) {
                    String wptName = wpt.getName();
                    entries.add(new Entry(file,
                            wptName == null || wptName.trim().isEmpty() ? title : wptName.trim(),
                            true, NO_LENGTH, wpt.getLat(), wpt.getLon()));
                }
                continue;
            }
            try (FileInputStream in = new FileInputStream(file)) {
                entries.add(new Entry(file, title, false, new Gpx2Fit(name, in, new Gpx2FitOptions()).getLength()));
            } catch (Exception e) {
                Log.warn("Could not read {}: {}", name, e.toString());
                entries.add(new Entry(file, title, false, NO_LENGTH));
            }
        }
        return entries;
    }

    /**
     * The waypoints of a GPX file that holds only {@code <wpt>} elements (a
     * shared destination), or null for tracks, routes, FIT files and
     * unparseable files.
     */
    static List<WayPoint> locationsIn(File file) {
        String name = file.getName();
        if (!(name.endsWith(".gpx") || name.endsWith(".GPX")) || mentionsTrackOrRoute(file)) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            Gpx2Fit gpx = new Gpx2Fit(name, in, new Gpx2FitOptions());
            if (gpx.hasTrackOrRoute() || gpx.getWptPoints().isEmpty()) {
                return null;
            }
            return gpx.getWptPoints();
        } catch (Exception e) {
            Log.warn("Could not parse {}: {}", name, e.toString());
            return null;
        }
    }

    /**
     * Cheap pre-check so a directory of large tracks is not parsed fully just
     * to find the locations: track and route points show up early in a file.
     */
    private static boolean mentionsTrackOrRoute(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            String tail = "";
            int len;
            while ((len = in.read(buf)) > 0) {
                String chunk = tail + new String(buf, 0, len, StandardCharsets.ISO_8859_1);
                if (chunk.contains("trkpt") || chunk.contains("rtept")) {
                    return true;
                }
                tail = chunk.substring(Math.max(0, chunk.length() - 8));
            }
        } catch (IOException e) {
            return true;
        }
        return false;
    }

    static String withoutExtension(String name) {
        return FileTypes.isTrackFile(name) ? name.substring(0, name.length() - 4) : name;
    }
}
