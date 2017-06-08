package org.surfsite.gexporter;

import android.support.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;

import fi.iki.elonen.NanoHTTPD;

import java.io.PushbackInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class WebServer extends NanoHTTPD {
    private static final Logger Log = LoggerFactory.getLogger(NanoHTTPD.class);

    private File mRootDir;
    private File mCacheDir;
    private Gpx2FitOptions mGpx2FitOptions;

    public WebServer(File rootDir, File cacheDir, int port, Gpx2FitOptions options)
            throws IOException, NoSuchAlgorithmException {

        super(port);
        mRootDir = rootDir;
        mCacheDir = cacheDir;
        mGpx2FitOptions = options;
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
            }

            boolean doShort = false;
            if (parms.containsKey("short") && parms.get("short").get(0).equals("1")) {
                doShort = true;
                Log.debug("doShort == true");
            }

            String path;
            if(uri.equals("/dir.json")){
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

                String ret="{ \"tracks\" : [";
                for (String aFilelist : filelist) {
                    if (aFilelist.endsWith(".fit") || aFilelist.endsWith(".FIT") || aFilelist.endsWith(".gpx") || aFilelist.endsWith(".GPX")  ) {
                        String url = null;
                        try {
                            if (doShort) {
                                url = URLEncoder.encode(aFilelist, "UTF-8");
                            }
                            else {
                                url = "http://127.0.0.1:" + this.getListeningPort() + "/" + URLEncoder.encode(aFilelist, "UTF-8");
                            }
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        String courseName = getCourseName(aFilelist);

                        ret += String.format("{ \"title\": \"%s\", \"url\": \"%s\"  },\n", courseName, url);
                    }
                }
                ret = ret.substring(0, ret.length()-2);
                ret += "]}";
                return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, MIME_JSON, ret);
            }

            path = uri;
            File src = null;
            try{
                if(path.endsWith(".json")){
                    mime_type = MIME_JSON;
                } else if(path.endsWith(".fit") || path.endsWith(".FIT")) {
                    mime_type = MIME_FIT;
                    src = new File(mRootDir, path);
                } else if(path.endsWith(".gpx") || path.endsWith(".GPX")) {
                    src = new File(mRootDir, path);

                    if (doGPXonly) {
                        mime_type = MIME_GPX;
                    } else {
                        String courseName = getCourseName(src.getName());

                        Gpx2Fit loader = new Gpx2Fit(courseName, new FileInputStream(src), mGpx2FitOptions);
                        src = new File(mCacheDir, path + ".fit");
                        Log.warn("Generating {}", src.getAbsolutePath());
                        loader.writeFit(src);
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
                // Open file from SD Card
                InputStream descriptor = new FileInputStream(src);
                Log.warn("Serving bytes: {}", src.length());
                return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, mime_type,
                        descriptor, src.length());

            } catch(IOException ioe) {
                Log.error("Serving exception {}", ioe.toString());
                return errorResponse(ioe);
            }

        }
        return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", "Not found");
    }

    @NonNull
    private Response errorResponse(Exception e) {
        return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON,
                "{ \"error\" : \"" + e.toString() + "\" } ");
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
