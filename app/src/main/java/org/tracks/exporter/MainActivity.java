package org.tracks.exporter;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.widget.TextView;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;

public class MainActivity extends AppCompatActivity {
    private WebServer server;
    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
/*
            InputStream keystoreStream = getResources().openRawResource(R.raw.keystore);
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            keystore.load(keystoreStream, "123456".toCharArray());
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, "123456".toCharArray());

            server = new WebServer(keystore, keyManagerFactory, getCacheDir(), 22222);
*/
            server = new WebServer(getCacheDir(), 22222);
            server.start();
            Log.w("Httpd", "Web server initialized.");
        } catch (IOException e) {
            Log.w("Httpd", "The server could not start. " + e.getLocalizedMessage());
        } catch (NoSuchAlgorithmException e) {
            Log.w("Httpd", "The server could not start. " + e.getLocalizedMessage());
            /*
        } catch (KeyManagementException e) {
            Log.w("Httpd", "The server could not start. " + e.getLocalizedMessage());
        } catch (CertificateException e) {
            Log.w("Httpd", "The server could not start. " + e.getLocalizedMessage());
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            Log.w("Httpd", "The server could not start. " + e.getLocalizedMessage());
            */
        }

        mTextView = (TextView) findViewById(R.id.textv);
        String rootdir = Environment.getExternalStorageDirectory().getAbsolutePath() +
                "/Download/";
        FilenameFilter filenameFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if (name.endsWith(".fit") || name.endsWith(".FIT") || name.endsWith(".gpx") || name.endsWith(".GPX")) {
                    return true;
                }
                return false;
            }
        };
        String[] filelist = new File(rootdir).list(filenameFilter);
        String txt = "Serving from " + rootdir + ":\n\n" + TextUtils.join("\n", filelist);
        mTextView.setText(txt);
    }

    // DON'T FORGET to stop the server
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (server != null)
            server.stop();
    }
}
