package org.surfsite.gexporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;

import fi.iki.elonen.NanoHTTPD;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


class WebServer extends NanoHTTPD {
    private static final Logger Log = LoggerFactory.getLogger(NanoHTTPD.class);

    private File mRootDir;
    private File mCacheDir;
    private GpxToFitOptions mGpxToFitOptions;

    WebServer(
            /* KeyStore keystore, KeyManagerFactory keyManagerFactory, */
            File rootDir, File cacheDir, int port, GpxToFitOptions options) throws IOException, NoSuchAlgorithmException /*, KeyManagementException, KeyStoreException*/ {

        super(port);
        mRootDir = rootDir;
        mCacheDir = cacheDir;
        mGpxToFitOptions = options;

        //System.setProperty("javax.net.ssl.trustStore", new File("src/test/resources/keystore.jks").getAbsolutePath());
        // makeSecure(NanoHTTPD.makeSSLSocketFactory(keystore, keyManagerFactory), null);
    }

    private static final String MIME_JSON = "application/json";
    private static final String MIME_GPX = "application/gpx+xml";
    private static final String MIME_FIT = "application/fit";

    @Override public Response serve(IHTTPSession session) {
        String mime_type = NanoHTTPD.MIME_HTML;
        Method method = session.getMethod();
        Map<String, List<String>> parms = session.getParameters();

        String uri = session.getUri();
        System.out.println(method + " '" + uri + "' ");
        // Open file from SD Card

        if(method.toString().equalsIgnoreCase("GET")) {
            boolean doGPXonly = false;
            if (parms.containsKey("type") && parms.get("type").get(0).equals("GPX")) {
                doGPXonly = true;
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
                    return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, "{ error: \"No permission or no files\" } ");
                }

                Arrays.sort(filelist);

                String ret="{ \"tracks\" : [";
                for (String aFilelist : filelist) {
                    if (aFilelist.endsWith(".fit") || aFilelist.endsWith(".FIT") || aFilelist.endsWith(".gpx") || aFilelist.endsWith(".GPX")  ) {
                        String url = null;
                        try {
                            url = "http://127.0.0.1:" + this.getListeningPort() + "/" + URLEncoder.encode(aFilelist, "UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        ret += "{ \"title\": \"" + aFilelist + "\", \"url\": \"" + url + "\" },";
                    }
                }
                ret = ret.substring(0, ret.length()-1);
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
                        Gpx2Fit loader = new Gpx2Fit(src, mGpxToFitOptions);
                        src = new File(mCacheDir, path + ".fit");
                        Log.warn("Httpd", "Generating " + src.getAbsolutePath());
                        loader.writeFit(src);
                        mime_type = MIME_FIT;
                    }
                }
            }catch(Exception e){
                Log.error("Httpd", "Error Serving:", e);

                return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, "{ error: \"" + e.toString() + "\" } ");
            }

            if (src == null) {
                Log.warn("Httpd", "src == null");

                return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", "Not found");
            }

            try {
                // Open file from SD Card
                InputStream descriptor = new FileInputStream(src);
                Log.warn("Httpd", "Serving bytes: " + src.length());
                return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, mime_type, descriptor, src.length());

            } catch(IOException ioe) {
                Log.warn("Httpd", ioe.toString());
                return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, "{ error: \"" + ioe.toString() + "\" } ");
            }

        }
        return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", "Not found");
    }
}
