package org.surfsite.gexporter;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class WebServer extends NanoHTTPD {
    private static final Logger Log = LoggerFactory.getLogger(NanoHTTPD.class);

    private File mRootDir;
    private File mCacheDir;
    private Gpx2FitOptions mGpx2FitOptions;

    public WebServer(File rootDir, File cacheDir, int port, Gpx2FitOptions options)
            throws IOException, NoSuchAlgorithmException {

        // Bind loopback only: the watch reaches us through the on-phone BLE
        // proxy at 127.0.0.1, so there is no reason to expose files to the LAN.
        super("127.0.0.1", port);
        mRootDir = rootDir;
        mCacheDir = cacheDir;
        mGpx2FitOptions = options;
    }

    File getRootDir() {
        return mRootDir;
    }

    /**
     * Resolve a request path against the served root, rejecting anything that
     * escapes it (path traversal). Returns null if the path is outside root.
     */
    private File resolveInRoot(String path) throws IOException {
        File root = mRootDir.getCanonicalFile();
        File target = new File(root, path).getCanonicalFile();
        String rootPath = root.getPath();
        if (target.getPath().equals(rootPath)
                || target.getPath().startsWith(rootPath + File.separator)) {
            return target;
        }
        Log.warn("Rejected path outside root: {}", path);
        return null;
    }

    private static final String MIME_JSON = "application/json";
    private static final String MIME_GPX = "application/gpx+xml";
    private static final String MIME_FIT = "application/fit";

    @Override public Response serve(IHTTPSession session) {
        String mime_type = NanoHTTPD.MIME_HTML;
        Method method = session.getMethod();
        Map<String, List<String>> parms = session.getParameters();

        String uri = session.getUri();
        Log.info("{} '{}'", method, uri);

        if(method.toString().equalsIgnoreCase("GET")) {
            boolean doGPXonly = false;
            if (parms.containsKey("type") && parms.get("type").get(0).equals("GPX")) {
                doGPXonly = true;
                Log.debug("doGPXonly == true");
            }

            boolean doShort = false;
            if (parms.containsKey("short") && parms.get("short").get(0).equals("1")) {
                doShort = true;
                Log.debug("doShort == true");
            }

            boolean doLongname = false;
            if (parms.containsKey("longname") && parms.get("longname").get(0).equals("1")) {
                doLongname = true;
                Log.debug("doLongname == true");
            }

            // Sent by gimporter versions that save shared locations as
            // waypoints; older ones would try to import them as courses.
            boolean doLocations = parms.containsKey("locations") && parms.get("locations").get(0).equals("1");

            if(uri.equals("/dir.json")){
                return getDir(doGPXonly, doShort, doLongname, doLocations);
            }

            String path = uri;
            File src = null;
            boolean deleteAfterServing = false;
            try{
                if(path.endsWith(".json")){
                    mime_type = MIME_JSON;
                } else if(path.endsWith(".fit") || path.endsWith(".FIT")) {
                    mime_type = MIME_FIT;
                    src = resolveInRoot(path);
                } else if(path.endsWith(".gpx") || path.endsWith(".GPX")) {
                    File gpx = resolveInRoot(path);

                    if (gpx == null) {
                        src = null;
                    } else if (doGPXonly) {
                        src = gpx;
                        mime_type = MIME_GPX;
                    } else {
                        String courseName = (doLongname ? gpx.getName() : getCourseName(gpx.getName()));

                        // Every request converts with the current options into
                        // its own temp file, which is deleted once served.
                        File tmp = File.createTempFile(CONVERTED_PREFIX, ".fit", mCacheDir);
                        try (FileInputStream fis = new FileInputStream(gpx)) {
                            Gpx2Fit loader = new Gpx2Fit(courseName, fis, mGpx2FitOptions);
                            Log.debug("Converting {}", gpx.getName());
                            loader.writeFit(tmp);
                        } catch (Exception e) {
                            tmp.delete();
                            throw e;
                        }
                        src = tmp;
                        deleteAfterServing = true;
                        mime_type = MIME_FIT;
                    }
                }
            }catch(Exception e){
                Log.error("Error Serving:", e);

                return errorResponse(e);
            }

            if (src == null) {
                Log.warn("src == null");

                return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", "Not found");
            }

            try {
                long length = src.length();
                InputStream descriptor = deleteAfterServing ? new DeleteOnCloseInputStream(src) : new FileInputStream(src);
                Log.debug("Serving bytes: {}", length);
                return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, mime_type, descriptor, length);

            } catch(IOException ioe) {
                Log.error("Serving exception {}", ioe.toString());
                return errorResponse(ioe);
            }

        }
        return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", "Not found");
    }

    @NonNull
    private Response getDir(boolean doGPXonly, boolean doShort, boolean doLongname, boolean doLocations) {
        FilenameFilter filenameFilter;
        if (doGPXonly) {
            filenameFilter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".gpx") || name.endsWith(".GPX");
                }
            };
        } else {
            filenameFilter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".fit") || name.endsWith(".FIT") || name.endsWith(".gpx") || name.endsWith(".GPX");
                }
            };
        }
        String[] filelist = mRootDir.list(filenameFilter);

        if (filelist == null) {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, "{ \"error\" : \"No permission or no files\" } ");
        }

        Arrays.sort(filelist);

        // Build the response with a JSON serializer so filenames containing
        // quotes, backslashes or control characters can't produce invalid JSON
        // (the watch rejects the whole list if any entry is malformed).
        List<Map<String, String>> tracks = new ArrayList<>();
        for (String aFilelist : filelist) {
            if (aFilelist.endsWith(".fit") || aFilelist.endsWith(".FIT") || aFilelist.endsWith(".gpx") || aFilelist.endsWith(".GPX")  ) {
                String url;
                try {
                    String encoded = URLEncoder.encode(aFilelist, "UTF-8");
                    url = doShort ? encoded : "http://127.0.0.1:" + this.getListeningPort() + "/" + encoded;
                } catch (UnsupportedEncodingException e) {
                    // UTF-8 is always available; skip the entry rather than emit a null url.
                    Log.error("Could not encode {}", aFilelist, e);
                    continue;
                }
                String courseName = (doLongname ? aFilelist : getCourseName(aFilelist));

                List<WayPoint> locations = ServedFiles.locationsIn(new File(mRootDir, aFilelist));
                if (locations != null && !doLocations) {
                    // An older gimporter cannot use a location and would
                    // fail to convert it to a course; do not offer it.
                    continue;
                }
                if (locations != null) {
                    // A destination, not a course: the watch saves each one as
                    // a location from lat/lon, so no download is needed. The
                    // coordinates are strings because the watch would parse
                    // JSON numbers as 32-bit floats (~1 m error).
                    for (WayPoint wpt : locations) {
                        String wptName = wpt.getName();
                        Map<String, String> loc = new LinkedHashMap<>();
                        loc.put("title", (wptName == null || wptName.trim().isEmpty())
                                ? getCourseName(aFilelist) : wptName.trim());
                        loc.put("url", url);
                        loc.put("lat", String.format(Locale.ROOT, "%.7f", wpt.getLat()));
                        loc.put("lon", String.format(Locale.ROOT, "%.7f", wpt.getLon()));
                        loc.put("hash", WatchContent.fingerprint(loc.get("title"), wpt.getLat(), wpt.getLon()));
                        tracks.add(loc);
                    }
                    continue;
                }

                Map<String, String> track = new LinkedHashMap<>();
                track.put("title", courseName);
                track.put("url", url);
                track.put("hash", WatchContent.fingerprint(new File(mRootDir, aFilelist)));
                tracks.add(track);
            }
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("tracks", tracks);
        String ret = new Gson().toJson(root);
        Log.debug("Return {}", ret);
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, MIME_JSON, ret);
    }

    /** Name prefix of the temporary FIT files of a conversion. */
    static final String CONVERTED_PREFIX = "converted";

    /** A converted file, deleted when NanoHTTPD has sent it and closes the stream. */
    private static final class DeleteOnCloseInputStream extends FileInputStream {
        private final File mFile;

        DeleteOnCloseInputStream(File file) throws FileNotFoundException {
            super(file);
            mFile = file;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                if (!mFile.delete()) {
                    Log.warn("Could not delete {}", mFile);
                }
            }
        }
    }

    @NonNull
    private Response errorResponse(Exception e) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", String.valueOf(e.getMessage()));
        return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON,
                new Gson().toJson(body));
    }

    @NonNull
    public static String getCourseName(String courseName) {
        if (courseName.endsWith(".fit") || courseName.endsWith(".FIT")
                || courseName.endsWith(".gpx") || courseName.endsWith(".GPX")  ) {
            courseName = courseName.substring(0, courseName.length()-4);
        }

        if (courseName.getBytes().length > 15) {
            courseName = courseName.substring(0, 15);
            while (courseName.getBytes().length > 15)
                courseName = courseName.substring(0, courseName.length()-1);
        }
        return courseName;
    }
}
