package org.surfsite.gexporter;

import android.os.Environment;
import android.util.Log;

import org.surfsite.gexporter.GPXLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import fi.iki.elonen.NanoHTTPD;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;


class WebServer extends NanoHTTPD {
    File cacheDir;

    WebServer(
            /* KeyStore keystore, KeyManagerFactory keyManagerFactory, */
            File cacheDir, int port) throws IOException, NoSuchAlgorithmException /*, KeyManagementException, KeyStoreException*/ {
        super(port);
        this.cacheDir = cacheDir;
        //System.setProperty("javax.net.ssl.trustStore", new File("src/test/resources/keystore.jks").getAbsolutePath());
        // makeSecure(NanoHTTPD.makeSSLSocketFactory(keystore, keyManagerFactory), null);
    }

    private static final String MIME_JSON = "application/json";
    private static final String MIME_GPX = "application/gpx+xml";
    private static final String MIME_FIT = "application/fit";

    @Override public Response serve(IHTTPSession session) {
        String mime_type = NanoHTTPD.MIME_HTML;
        Method method = session.getMethod();
        String uri = session.getUri();
        System.out.println(method + " '" + uri + "' ");
        // Open file from SD Card
        String rootdir = Environment.getExternalStorageDirectory().getAbsolutePath() +
                "/Download/";

        if(method.toString().equalsIgnoreCase("GET")) {
            String path;
            if(uri.equals("/dir.json")){
                String[] filelist = new File(rootdir).list();
                if (filelist == null) {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, "{ error: \"No permission\" } ");
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
                return newFixedLengthResponse(Response.Status.OK, MIME_JSON, ret);
            }

            path = uri;
            File src = null;
            try{
                if(path.endsWith(".json")){
                    mime_type = MIME_JSON;
                } else if(path.endsWith(".fit") || path.endsWith(".FIT")) {
                    mime_type = MIME_FIT;
                    src = new File(rootdir + path);
                } else if(path.endsWith(".gpx") || path.endsWith(".GPX")) {
                    src = new File(rootdir + path);

                    GPXLoader loader = new GPXLoader(src);
                    src = new File(cacheDir, path + ".fit");
                    Log.w("Httpd", "Generating " + src.getAbsolutePath());
                    loader.writeFit(src);
                    mime_type = MIME_FIT;
                }
            }catch(Exception e){
                Log.e("Httpd", "Error Serving:", e);

                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, "{ error: \"" + e.toString() + "\" } ");
            }
            if (src == null) {
                Log.w("Httpd", "src == null");

                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", "Not found");
            }

            try {
                // Open file from SD Card
                InputStream descriptor = new FileInputStream(src);
                Log.w("Httpd", "Serving bytes: " + src.length());
                return newFixedLengthResponse(Response.Status.OK, mime_type, descriptor, src.length());

            } catch(IOException ioe) {
                Log.w("Httpd", ioe.toString());
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, "{ error: \"" + ioe.toString() + "\" } ");
            }

        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", "Not found");
    }
}
