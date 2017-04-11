package org.tracks.exporter;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import fi.iki.elonen.NanoHTTPD;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;


class WebServer extends NanoHTTPD {
    WebServer(int port) {
        super(port);
    }

    private static final String MIME_JSON = "application/json";
    private static final String MIME_GPX = "application/gpx+xml";
    private static final String MIME_FIT = "application/fit";

    @Override public Response serve(IHTTPSession session) {
        String mime_type = NanoHTTPD.MIME_HTML;
        Method method = session.getMethod();
        String uri = session.getUri();
        System.out.println(method + " '" + uri + "' ");
        InputStream descriptor = null;
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

                String ret="{ \"tracks\" : [";
                for (String aFilelist : filelist) {
                    if (aFilelist.endsWith(".gpx") || aFilelist.endsWith(".fit") || aFilelist.endsWith(".GPX") || aFilelist.endsWith(".FIT") ) {
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
            try{
                if(path.endsWith(".json")){
                    mime_type = MIME_JSON;
                } else if(path.endsWith(".fit") || path.endsWith(".FIT")) {
                    mime_type = MIME_FIT;
                } else if(path.endsWith(".gpx") || path.endsWith(".GPX")) {
                    mime_type = MIME_GPX;
                }
            }catch(Exception e){
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, "{ error: \"" + e.toString() + "\" } ");
            }

            try {
                // Open file from SD Card
                File src = new File(rootdir + path);
                descriptor = new FileInputStream(src);
                return newFixedLengthResponse(Response.Status.OK, mime_type, descriptor, src.length());

            } catch(IOException ioe) {
                Log.w("Httpd", ioe.toString());
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, "{ error: \"" + ioe.toString() + "\" } ");
            }

        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", "Not found");
    }
}
