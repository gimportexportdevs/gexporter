package org.surfsite.gexporter;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What dir.json needs to know about a served file: its fingerprint and, for a
 * shared destination, its waypoints. Both mean reading the whole file, which
 * for a folder of large tracks on a slow phone takes longer than the watch
 * waits for the list. The cache is process-wide, so it outlives the web
 * server, which is restarted whenever the main screen comes back.
 */
final class FileInfoCache {
    static final class Info {
        final String fingerprint;
        /** The waypoints of a shared destination, null for a track. */
        final List<WayPoint> locations;
        private final long length;
        private final long lastModified;

        private Info(File file) {
            length = file.length();
            lastModified = file.lastModified();
            locations = ServedFiles.locationsIn(file);
            fingerprint = WatchContent.fingerprint(file);
        }

        private boolean matches(File file) {
            return length == file.length() && lastModified == file.lastModified();
        }
    }

    private static final ConcurrentHashMap<String, Info> sInfo = new ConcurrentHashMap<>();

    private FileInfoCache() {
    }

    /** For tests: whether the cache holds an entry for this file. */
    static boolean contains(File file) {
        return sInfo.containsKey(file.getAbsolutePath());
    }

    /** The info of a file, read again only when the file has changed. */
    static Info get(File file) {
        String key = file.getAbsolutePath();
        Info info = sInfo.get(key);
        if (info == null || !info.matches(file)) {
            info = new Info(file);
            sInfo.put(key, info);
        }
        return info;
    }

    /**
     * Read the track files of a directory ahead of the first list request,
     * and forget files that are gone (a new share empties the served folder).
     */
    static void warm(File dir) {
        sInfo.keySet().removeIf(path -> !new File(path).isFile());
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && FileTypes.isTrackFile(file.getName())) {
                get(file);
            }
        }
    }
}
