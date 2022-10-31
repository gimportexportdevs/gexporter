package org.surfsite.gexporter;

import static android.os.Environment.DIRECTORY_DOWNLOADS;

import android.Manifest;
import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import com.garmin.android.connectiq.ConnectIQ;
import com.garmin.android.connectiq.IQApp;
import com.garmin.android.connectiq.IQDevice;
import com.garmin.android.connectiq.exception.InvalidStateException;
import com.garmin.android.connectiq.exception.ServiceUnavailableException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final Logger Log = LoggerFactory.getLogger(MainActivity.class);
    private static final int GEXPORTER_OPEN_DIR = 1;

    @Nullable
    private WebServer server = null;
    private TextView mTextView;
    private Spinner mSpeedUnit;
    private EditText mSpeed;
    private CheckBox mForceSpeed;
    private CheckBox mUse3DDistance;
    private CheckBox mInjectCoursePoints;
    private CheckBox mUseWalkingGrade;
    private CheckBox mReducePoints;
    private EditText mMaxPoints;
    private Gpx2FitOptions mGpx2FitOptions = null;
    File mDirectory = null;
    private final NumberFormat mNumberFormat = NumberFormat.getInstance(Locale.getDefault());
    ArrayList<Uri> mUris;
    String mType;
    ContentResolver mCR;

    private final static int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 300;

    private final static String CONNECT_IQ_GIMPORTER_APP = "9B0A09CF-C89E-4F7C-A5E4-AB21400EE424";
    private final static String CONNECT_IQ_GIMPORTER_APP_STOREENTRY = "DE11ADC4-FDBB-40B5-86AC-7F93B47EA5BB";

    // auto launch doesn't seem to work for widgets unfortunately
    private final static String CONNECT_IQ_GIMPORTER_WIDGET = "B5FD4C5F-E0F8-48E8-8A03-E37E86971CEB";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCR = getContentResolver();

        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mSpeedUnit = (Spinner) findViewById(R.id.SPspeedUnits);
        ArrayAdapter<CharSequence> mSpeedUnitAdapter = ArrayAdapter.createFromResource(this,
                R.array.speedunits, android.R.layout.simple_spinner_item);
        mSpeedUnitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpeedUnit.setAdapter(mSpeedUnitAdapter);
        mSpeedUnit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long selId) {
                if (mGpx2FitOptions != null)
                    mGpx2FitOptions.setSpeedUnit(pos);
                setSpeedText(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                if (mGpx2FitOptions != null)
                    mGpx2FitOptions.setSpeedUnit(0);
            }
        });

        mSpeed = (EditText) findViewById(R.id.editSpeed);
        char separator = DecimalFormatSymbols.getInstance().getDecimalSeparator();
        mSpeed.setKeyListener(DigitsKeyListener.getInstance("0123456789:" + separator));
        mSpeed.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (getCurrentFocus() != mSpeed) {
                    return;
                }

                if (editable.length() > 0 && mGpx2FitOptions != null) {
                    double speed;
                    try {
                        String s = editable.toString();
                        String[] as = s.split(":");
                        Collections.reverse(Arrays.asList(as));
                        speed = mNumberFormat.parse(as[0]).doubleValue();
                        if (as.length > 1) {
                            speed = mNumberFormat.parse(as[1]).doubleValue() + speed / 60.0;
                        }
                        if (as.length > 2) {
                            speed = mNumberFormat.parse(as[2]).doubleValue() * 60 + speed;
                        }
                    } catch (ParseException e) {
                        speed = .0;
                    }
                    if (speed > .0) {
                        switch (mGpx2FitOptions.getSpeedUnit()) {
                            case 0:
                                speed = 1000.0 / 60.0 / speed;
                                break;
                            case 1:
                                speed = speed * 1000.0 / 3600.0;
                                break;
                            case 2:
                                speed = 1609.344 / 60.0 / speed;
                                break;
                            case 3:
                                speed = speed * 1609.344 / 3600.0;
                        }
                    }
                    mGpx2FitOptions.setSpeed(speed);
                }
            }
        });

        mForceSpeed = (CheckBox) findViewById(R.id.CBforceSpeed);
        mForceSpeed.setOnClickListener(this);

        mUse3DDistance = (CheckBox) findViewById(R.id.CBuse3D);
        mUse3DDistance.setOnClickListener(this);

        mInjectCoursePoints = (CheckBox) findViewById(R.id.CBinject);
        mInjectCoursePoints.setOnClickListener(this);

        mUseWalkingGrade = (CheckBox) findViewById(R.id.CBuseWalkingGrade);
        mUseWalkingGrade.setOnClickListener(this);

        mReducePoints = (CheckBox) findViewById(R.id.CBreducePoints);
        mReducePoints.setOnClickListener(this);

        mMaxPoints = (EditText) findViewById(R.id.editPointNumber);
        mMaxPoints.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.length() > 0 && mGpx2FitOptions != null && mReducePoints.isChecked())
                    mGpx2FitOptions.setMaxPoints(Integer.parseInt(editable.toString()));
            }
        });

        mTextView = (TextView) findViewById(R.id.textv);

        View settingsView = findViewById(R.id.settings_content);
        ImageButton expandButton = findViewById(R.id.expand_settings);
        findViewById(R.id.expand_touch_area).setOnClickListener(v -> expandButton.performClick());
        expandButton.setOnClickListener(v -> {
            TransitionManager.beginDelayedTransition(findViewById(android.R.id.content), new AutoTransition());
            if (settingsView.getVisibility() == View.VISIBLE) {
                settingsView.setVisibility(View.GONE);
                expandButton.setImageResource(R.drawable.ic_expand_more);
            } else {
                settingsView.setVisibility(View.VISIBLE);
                expandButton.setImageResource(R.drawable.ic_expand_less);
            }
        });

        initConnectIQ();
    }

    private void initConnectIQ() {
        ConnectIQ connectIQ = ConnectIQ.getInstance();
        connectIQ.initialize(this, true, new ConnectIQ.ConnectIQListener() {
            @Override
            public void onSdkReady() {
                launchIQApp(connectIQ);
            }

            @Override
            public void onInitializeError(ConnectIQ.IQSdkErrorStatus iqSdkErrorStatus) {
                ((TextView) findViewById(R.id.connect_infotext)).setText(R.string.connect_init_failed);
            }

            @Override
            public void onSdkShutDown() {
                // nothing needed
            }
        });
    }

    private void launchIQApp(ConnectIQ connectIQ) {
        try {
            List<IQDevice> devices = connectIQ.getConnectedDevices();
            if (devices != null && devices.size() > 0) {
                for (final IQDevice device : devices) {

                    connectIQ.getApplicationInfo(CONNECT_IQ_GIMPORTER_APP, device, new ConnectIQ.IQApplicationInfoListener() {

                        @Override
                        public void onApplicationInfoReceived(IQApp iqApp) {
                            try {
                                connectIQ.openApplication(device, iqApp, (iqDevice, iqApp1, iqOpenApplicationStatus) -> {
                                    if (iqOpenApplicationStatus == ConnectIQ.IQOpenApplicationStatus.PROMPT_SHOWN_ON_DEVICE || iqOpenApplicationStatus == ConnectIQ.IQOpenApplicationStatus.APP_IS_ALREADY_RUNNING) {
                                        ((TextView) findViewById(R.id.connect_infotext)).setText(R.string.connect_connected);
                                        ((CardView) findViewById(R.id.connect_card)).setCardBackgroundColor(0xff77cc77);
                                    }
                                });
                            } catch (InvalidStateException | ServiceUnavailableException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onApplicationNotInstalled(String s) {
                            findViewById(R.id.connect_card).setOnClickListener(v -> {
                                try {
                                    connectIQ.openStore(CONNECT_IQ_GIMPORTER_APP_STOREENTRY);
                                } catch (InvalidStateException | ServiceUnavailableException e) {
                                    e.printStackTrace();
                                }
                            });

                            try {
                                connectIQ.getApplicationInfo(CONNECT_IQ_GIMPORTER_WIDGET, device, new ConnectIQ.IQApplicationInfoListener() {
                                    @Override
                                    public void onApplicationInfoReceived(IQApp iqApp) {
                                        ((TextView) findViewById(R.id.connect_infotext)).setText(R.string.connect_app_only_widget_installed);
                                    }

                                    @Override
                                    public void onApplicationNotInstalled(String s) {
                                        ((TextView) findViewById(R.id.connect_infotext)).setText(R.string.connect_app_not_installed);
                                        ((CardView) findViewById(R.id.connect_card)).setCardBackgroundColor(0xffee7777);
                                    }
                                });
                            } catch (InvalidStateException | ServiceUnavailableException e) {
                                e.printStackTrace();
                            }

                        }
                    });
                }
            } else {
                ((TextView) findViewById(R.id.connect_infotext)).setText(R.string.connect_no_device);
                ((CardView) findViewById(R.id.connect_card)).setCardBackgroundColor(0xffee7777);
            }
        } catch (InvalidStateException | ServiceUnavailableException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.help) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/gimportexportdevs/gexporter/wiki/Help"));
            startActivity(browserIntent);
        }
        return true;
    }

    private void setSpeedText(int pos) {
        double speed = mGpx2FitOptions.getSpeed();
        if (Double.isNaN(speed))
            speed = 10.0;

        String val = null;
        switch (pos) {
            case 0:
                speed = 1000.0 / speed / 60.0;
                if (speed < 60) {
                    val = String.format(Locale.getDefault(), "%d:%02d", (int) speed, ((int) (speed * 60.0 + 0.5) % 60));
                } else {
                    val = String.format(Locale.getDefault(), "%d:%02d:%02d", ((int) speed) / 60,
                            ((int) speed % 60), ((int) (speed * 60.0 + 0.5) % 60));
                }
                break;
            case 1:
                speed = speed / 1000.0 * 3600.0;
                val = String.format(Locale.getDefault(), "%.2f", speed);
                break;
            case 2:
                speed = 1609.344 / speed / 60.0;
                if (speed < 60) {
                    val = String.format(Locale.getDefault(), "%d:%02d", (int) speed, ((int) (speed * 60.0 + 0.5) % 60));
                } else {
                    val = String.format(Locale.getDefault(), "%d:%02d:%02d", ((int) speed) / 60,
                            ((int) speed % 60), ((int) (speed * 60.0 + 0.5) % 60));
                }
                break;
            case 3:
                speed = speed / 1609.344 * 3600.0;
                val = String.format(Locale.getDefault(), "%.2f", speed);
        }
        if (mSpeed != null && val != null)
            mSpeed.setText(val);
    }

    @Override
    public void onClick(View v) {
        if (mGpx2FitOptions == null)
            return;

        int id = v.getId();
        if (id == R.id.CBforceSpeed) {
            mGpx2FitOptions.setForceSpeed(mForceSpeed.isChecked());
        } else if (id == R.id.CBinject) {
            mGpx2FitOptions.setInjectCoursePoints(mInjectCoursePoints.isChecked());
        } else if (id == R.id.CBreducePoints) {
            if (mReducePoints.isChecked()) {
                String t = mMaxPoints.getText().toString();
                if (t.length() > 0)
                    mGpx2FitOptions.setMaxPoints(Integer.decode(t));
            } else
                mGpx2FitOptions.setMaxPoints(0);
        } else if (id == R.id.CBuse3D) {
            mGpx2FitOptions.setUse3dDistance(mUse3DDistance.isChecked());
        } else if (id == R.id.CBuseWalkingGrade) {
            mGpx2FitOptions.setWalkingGrade(mUseWalkingGrade.isChecked());
        } else if (id == R.id.file_button) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, GEXPORTER_OPEN_DIR);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @Nullable String[] permissions, @Nullable int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {// If request is cancelled, the result arrays are empty.
            if (grantResults != null && grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                serveFiles();

            } else {
                Log.error("Permissions: {}", grantResults);
                mTextView.setText(R.string.no_permission);
            }
        }
    }

    void rmdir(File f) throws IOException {
        if (f.isDirectory()) {
            for (File c : f.listFiles())
                rmdir(c);
        }
        if (!f.delete())
            throw new FileNotFoundException("Failed to delete file: " + f);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (server != null) {
            server.stop();
            server = null;
            clearTempDir();
        }
    }

    public void save(Gpx2FitOptions options) {
        Application app = getApplication();
        SharedPreferences mPrefs = app.getSharedPreferences(app.getApplicationInfo().name, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = mPrefs.edit();
        Gson gson = new Gson();
        ed.putString(options.getClass().getName(), gson.toJson(options));
        ed.apply();
    }

    public Gpx2FitOptions load() {
        Application app = getApplication();
        Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
        SharedPreferences mPrefs = app.getSharedPreferences(app.getApplicationInfo().name, Context.MODE_PRIVATE);
        String json = mPrefs.getString(Gpx2FitOptions.class.getName(), null);
        Gpx2FitOptions opts = null;
        if (json != null && json.length() > 0)
            opts = gson.fromJson(JsonParser.parseString(json).getAsJsonObject(), Gpx2FitOptions.class);
        if (opts != null)
            return opts;
        else
            return new Gpx2FitOptions();
    }


    @Override
    public void onPause() {
        super.onPause();
        save(mGpx2FitOptions);
    }

    @Override
    public void onResume() {
        super.onResume();  // Always call the superclass method first

        mGpx2FitOptions = load();

        setSpeedText(mGpx2FitOptions.getSpeedUnit());

        mForceSpeed.setChecked(mGpx2FitOptions.isForceSpeed());
        mUse3DDistance.setChecked(mGpx2FitOptions.isUse3dDistance());
        mInjectCoursePoints.setChecked(mGpx2FitOptions.isInjectCoursePoints());
        mUseWalkingGrade.setChecked(mGpx2FitOptions.isWalkingGrade());

        mReducePoints.setChecked(mGpx2FitOptions.getMaxPoints() > 0);
        int maxPoints = mGpx2FitOptions.getMaxPoints();
        if (maxPoints == 0)
            maxPoints = 1000;
        mMaxPoints.setText(String.format(Locale.getDefault(), "%d", maxPoints));

        mSpeedUnit.setSelection(mGpx2FitOptions.getSpeedUnit());
        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        Log.debug("{}", intent);
        mType = intent.getType();

        if (Intent.ACTION_SEND.equals(action)) {
            Log.debug("ACTION_SEND");
            Uri uri = intent.getData();
            if (uri == null)
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri == null) {
                uri = Uri.parse(intent.getStringExtra(Intent.EXTRA_TEXT));
            }

            if (uri == null) {
                Log.debug("{}", intent.toString());
            }

            if (uri != null) {
                Log.debug("URI {}: type {} scheme {}", uri, intent.getType(), intent.getScheme());
                mUris = new ArrayList<>();
                mUris.add(uri);
            }
        }
        if (Intent.ACTION_VIEW.equals(action)) {
            Log.debug("ACTION_VIEW");
            Uri uri = intent.getData();
            if (uri != null) {
                Log.debug("URI {}: type '{}'", uri, intent.getType());
                mUris = new ArrayList<>();
                mUris.add(uri);
            }
        }
        if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            Log.debug("ACTION_SEND_MULTIPLE");

            mUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        }

        if (mUris != null && server != null) {
            server.stop();
        }

        if (server == null) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
            ) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
            } else {
                serveFiles();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent returnIntent) {
        super.onActivityResult(requestCode, resultCode, returnIntent);

        // If the selection didn't work
        if (resultCode != RESULT_OK) {
            // Exit without doing anything else
            return;
        } else {
            if (requestCode == GEXPORTER_OPEN_DIR) {
                Uri uri = null;
                if (returnIntent != null) {
                    uri = returnIntent.getData();
                    mUris = new ArrayList<>();
                    mUris.add(uri);
                }
            }
        }
    }

    private void copyInputStreamToFile(InputStream in, File file) {
        try {
            OutputStream out = new FileOutputStream(file);
            byte[] buf = new byte[1024];
            int len;
            len = in.read(buf);
            String name = file.getAbsolutePath();
            if (!(name.endsWith(".fit") || name.endsWith(".FIT") || name.endsWith(".gpx") || name.endsWith(".GPX"))) {
                String sig = new String(Arrays.copyOf(buf, 8), StandardCharsets.UTF_8);
                if (sig.length() > 5 && (sig.startsWith("<?xml") || sig.endsWith("<?xml"))) {
                    //noinspection ResultOfMethodCallIgnored
                    file.renameTo(new File(name + ".gpx"));
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    file.renameTo(new File(name + ".fit"));
                }
            }
            while (len > 0) {
                out.write(buf, 0, len);
                len = in.read(buf);
            }
            out.close();
            in.close();
        } catch (Exception e) {
            Log.error("copyInputStreamToFile {}", e.toString());
        } finally {
            try {
                in.close();
            } catch (Exception e) {
                Log.error("Closing InputStream {}", e.toString());
            }
        }
    }

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = mCR.query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        result = cursor.getString(idx);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    protected void serveFiles() {
        String rootDirectory;
        String downloadDirectory = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS).getAbsolutePath();
        if (mUris == null) {
            rootDirectory = downloadDirectory;
        } else {
            clearTempDir();

            mDirectory = new File(getCacheDir(), Long.toString(System.nanoTime()));
            //noinspection ResultOfMethodCallIgnored
            mDirectory.mkdir();

            rootDirectory = mDirectory.getAbsolutePath();

            for (Uri uri : mUris) {
                String scheme = uri.getScheme();

                if (!scheme.equals("content") && !scheme.equals("file")) {
                    Log.debug("Skip URI {} scheme {}", uri, uri.getScheme());
                    continue;
                }

                DocumentFile file = null;

                try {
                    file = DocumentFile.fromTreeUri(getApplicationContext(), uri);
                } catch (IllegalArgumentException e) {
                    try {
                        file = DocumentFile.fromSingleUri(getApplicationContext(), uri);
                    } catch (IllegalArgumentException _e) {
                        ;
                    }
                }
                if (file == null)
                    continue;

                if (file.isFile()) {
                    Log.debug("Open URI {} scheme {}", uri, scheme);
                    try {
                        InputStream is = mCR.openInputStream(uri);
                        String name = getFileName(uri);
                        copyInputStreamToFile(is, new File(mDirectory, name));
                    } catch (FileNotFoundException e) {
                        Log.error("Exception Open URI:", e);
                    }
                } else if (file.isDirectory()) {
                    for (DocumentFile doc : file.listFiles()) {
                        Log.info("Open URI {} scheme {}", uri, scheme);
                        if (!doc.isFile())
                            continue;
                        try {
                            String name = getFileName(doc.getUri());
                            if (name.endsWith(".fit") || name.endsWith(".FIT") || name.endsWith(".gpx") || name.endsWith(".GPX")) {
                                InputStream is = mCR.openInputStream(doc.getUri());
                                copyInputStreamToFile(is, new File(mDirectory, name));
                            }
                        } catch (FileNotFoundException e) {
                            Log.error("Exception Open URI:", e);
                        }
                    }
                }
            }
        }

        File directory;

        if (rootDirectory.equals(downloadDirectory)) {
            directory = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS);
            Log.error("Using DIRECTORY_DOWNLOADS");
        } else {
            directory = new File(rootDirectory);
            Log.error("OpenDir {}", rootDirectory);
        }

        try {
            server = new WebServer(directory, getCacheDir(), 22222, mGpx2FitOptions);
            server.start();
            Log.info("Web server initialized.");
        } catch (IOException | NoSuchAlgorithmException e) {
            Log.error("The server could not start: {}", e.toString());
            mTextView.setText(R.string.no_server);
        }

        FilenameFilter filenameFilter = (dir, name) -> name.endsWith(".fit") || name.endsWith(".FIT") || name.endsWith(".gpx") || name.endsWith(".GPX");

        String[] fileList = directory.list(filenameFilter);

        if ((fileList == null) || (fileList.length == 0))
        {
            mTextView.setText("Please use the File Explorer and share files with this app!");
        } else {
            Arrays.sort(fileList);
            mTextView.setText(String.format(getResources().getString(R.string.serving_from), rootDirectory, TextUtils.join("\n", fileList)));
        }
    }

    private void clearTempDir() {
        if (mDirectory != null) {
            try {
                rmdir(mDirectory);
            } catch (IOException e) {
                Log.error("Failed to delete {} {}", mDirectory.getAbsolutePath(), e);
            } finally {
                mDirectory = null;
            }
        }
    }
}
