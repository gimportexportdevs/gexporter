package org.surfsite.gexporter;

import java.io.File;
import java.nio.charset.StandardCharsets;

/** Recognizing and naming files shared into gexporter. */
final class FileTypes {
    static final String FALLBACK_NAME = "shared";

    private FileTypes() {
    }

    static boolean isTrackFile(String name) {
        return name != null && (name.endsWith(".fit") || name.endsWith(".FIT")
                || name.endsWith(".gpx") || name.endsWith(".GPX"));
    }

    /**
     * The extension for a file of unknown name, judged by its first bytes:
     * ".fit" for a FIT header, ".gpx" for XML/GPX, or null for anything else
     * (a shared text file must not be served as a course).
     */
    static String sniffExtension(byte[] head, int len) {
        // FIT file header: byte 0 is the header size (12 or 14), bytes 8-11 are ".FIT"
        if (len >= 12 && (head[0] == 12 || head[0] == 14)
                && head[8] == '.' && head[9] == 'F' && head[10] == 'I' && head[11] == 'T') {
            return ".fit";
        }
        String text = new String(head, 0, Math.max(len, 0), StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        text = text.trim();
        if (text.startsWith("<?xml") || text.startsWith("<gpx")) {
            return ".gpx";
        }
        return null;
    }

    /**
     * A file name from a sharing app (e.g. a content provider's DISPLAY_NAME)
     * reduced to its last path segment, so it cannot escape the target
     * directory.
     */
    static String safeFileName(String displayName) {
        if (displayName == null) {
            return FALLBACK_NAME;
        }
        String name = new File(displayName.replace('\\', '/')).getName().trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return FALLBACK_NAME;
        }
        return name;
    }
}
