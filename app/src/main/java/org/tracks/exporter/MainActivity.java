package org.tracks.exporter;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;

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
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            TypedValue value = new TypedValue();
            InputStream keystoreStream = getResources().openRawResource(R.raw.keystore);
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            keystore.load(keystoreStream, "123456".toCharArray());
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, "123456".toCharArray());

            //String keystore = getResources().getResourceEntryName(R.raw.keystore);
            server = new WebServer(keystore, keyManagerFactory, getFilesDir(), 22222);
            server.start();
            Log.w("Httpd", "Web server initialized.");
        } catch(IOException e) {
            Log.w("Httpd", "The server could not start. " + e.getLocalizedMessage());
        } catch (NoSuchAlgorithmException e) {
            Log.w("Httpd", "The server could not start. " + e.getLocalizedMessage());
        } catch (KeyManagementException e) {
            Log.w("Httpd", "The server could not start. " + e.getLocalizedMessage());
        } catch (CertificateException e) {
            Log.w("Httpd", "The server could not start. " + e.getLocalizedMessage());
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            Log.w("Httpd", "The server could not start. " + e.getLocalizedMessage());
        }
    }

    // DON'T FORGET to stop the server
    @Override
    public void onDestroy()
    {
        super.onDestroy();
        if (server != null)
            server.stop();
    }
}
