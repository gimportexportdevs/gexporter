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
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final Logger Log = LoggerFactory.getLogger(MainActivity.class);
    private static final int GEXPORTER_OPEN_DIR = 1;
    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 300;

    private static final String CONNECT_IQ_GIMPORTER_APP = "9B0A09CF-C89E-4F7C-A5E4-AB21400EE424";
    private static final String CONNECT_IQ_GIMPORTER_APP_STOREENTRY = "DE11ADC4-FDBB-40B5-86AC-7F93B47EA5BB";
    private static final String CONNECT_IQ_GIMPORTER_WIDGET = "B5FD4C5F-E0F8-48E8-8A03-E37E86971CEB";

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
    private File mDirectory = null;
    private final NumberFormat mNumberFormat = NumberFormat.getInstance(Locale.getDefault());
    private ArrayList<Uri> mUris;
    // Text or geo: URI shared from a map app; served as a one-waypoint GPX.
    private String mSharedText;
    private SharedLocation mSharedLocation;
    // Resolved before the ConnectIQ SDK found the watch app; sent once it has.
    private SharedLocation mPendingPush;
    private ContentResolver mCR;

    private ConnectIQ mConnectIQ = null;
    private IQDevice mConnectedDevice = null;
    private IQApp mConnectedApp = null;
    private final Set<Long> mRegisteredDevices = new HashSet<>();
    
    private ActivityResultLauncher<Intent> openDocumentTreeLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable modern edge-to-edge using WindowCompat (Android 15+ compatible)
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        Log.debug("onCreate called");
        mCR = getContentResolver();

        // Initialize the activity result launcher
        openDocumentTreeLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    onActivityResult(GEXPORTER_OPEN_DIR, RESULT_OK, result.getData());
                }
            }
        );

        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Handle window insets for edge-to-edge display using modern approach
        View rootView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            
            // Apply padding to avoid system bar overlap
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            
            return WindowInsetsCompat.CONSUMED;
        });

        initializeUIComponents();
        initConnectIQ();
        Log.debug("onCreate completed");
    }

    private void initializeUIComponents() {
        // Initialize speed unit spinner
        mSpeedUnit = findViewById(R.id.SPspeedUnits);
        ArrayAdapter<CharSequence> speedUnitAdapter = ArrayAdapter.createFromResource(this,
                R.array.speedunits, android.R.layout.simple_spinner_item);
        speedUnitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpeedUnit.setAdapter(speedUnitAdapter);
        mSpeedUnit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mGpx2FitOptions != null) {
                    mGpx2FitOptions.setSpeedUnit(position);
                }
                setSpeedText(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (mGpx2FitOptions != null) {
                    mGpx2FitOptions.setSpeedUnit(0);
                }
            }
        });

        // Initialize speed input
        mSpeed = findViewById(R.id.editSpeed);
        char separator = DecimalFormatSymbols.getInstance().getDecimalSeparator();
        mSpeed.setKeyListener(DigitsKeyListener.getInstance("0123456789:" + separator));
        mSpeed.addTextChangedListener(new SpeedTextWatcher());

        // Initialize checkboxes
        mForceSpeed = findViewById(R.id.CBforceSpeed);
        mForceSpeed.setOnClickListener(this);

        mUse3DDistance = findViewById(R.id.CBuse3D);
        mUse3DDistance.setOnClickListener(this);

        mInjectCoursePoints = findViewById(R.id.CBinject);
        mInjectCoursePoints.setOnClickListener(this);

        mUseWalkingGrade = findViewById(R.id.CBuseWalkingGrade);
        mUseWalkingGrade.setOnClickListener(this);

        mReducePoints = findViewById(R.id.CBreducePoints);
        mReducePoints.setOnClickListener(this);

        // Initialize max points input
        mMaxPoints = findViewById(R.id.editPointNumber);
        mMaxPoints.addTextChangedListener(new MaxPointsTextWatcher());

        // Initialize text view
        mTextView = findViewById(R.id.textv);

        // Initialize expandable settings
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

        // Initialize file selection button
        findViewById(R.id.file_button).setOnClickListener(this);
    }

    private void initConnectIQ() {
        mConnectIQ = ConnectIQ.getInstance();
        mConnectIQ.initialize(this, true, new ConnectIQ.ConnectIQListener() {
            @Override
            public void onSdkReady() {
                launchIQApp(mConnectIQ);
            }

            @Override
            public void onInitializeError(ConnectIQ.IQSdkErrorStatus errorStatus) {
                TextView infoText = findViewById(R.id.connect_infotext);
                infoText.setText(R.string.connect_init_failed);
            }

            @Override
            public void onSdkShutDown() {
                // No action needed
            }
        });
    }

    private void launchIQApp(ConnectIQ connectIQ) {
        try {
            List<IQDevice> devices = connectIQ.getConnectedDevices();
            if (devices != null && !devices.isEmpty()) {
                for (IQDevice device : devices) {
                    connectIQ.getApplicationInfo(CONNECT_IQ_GIMPORTER_APP, device, new ConnectIQ.IQApplicationInfoListener() {
                        @Override
                        public void onApplicationInfoReceived(IQApp app) {
                            mConnectedDevice = device;
                            mConnectedApp = app;
                            onWatchAppFound(connectIQ, device, app);

                            // Register the GET_PORT listener as soon as we know the
                            // app is installed - independent of whether the user
                            // accepts the "open app" prompt below. Otherwise a
                            // dismissed prompt or non-success status leaves GET_PORT
                            // permanently unanswered for this process.
                            registerMessageListener(connectIQ, device, app);
                            // Before the "open app" prompt below, so the
                            // location is waiting when the app starts.
                            sendPendingLocation(device, app);

                            try {
                                connectIQ.openApplication(device, app, (iqDevice, iqApp, status) -> {
                                    if (status == ConnectIQ.IQOpenApplicationStatus.PROMPT_SHOWN_ON_DEVICE ||
                                            status == ConnectIQ.IQOpenApplicationStatus.APP_IS_ALREADY_RUNNING) {
                                        TextView infoText = findViewById(R.id.connect_infotext);
                                        infoText.setText(R.string.connect_connected);
                                        CardView connectCard = findViewById(R.id.connect_card);
                                        connectCard.setCardBackgroundColor(0xff77cc77);

                                        Log.info("Garmin app {} successfully connected to device {}",
                                                app.getApplicationId(), device.getDeviceIdentifier());
                                    }
                                });
                            } catch (InvalidStateException | ServiceUnavailableException e) {
                                Log.error("Error opening application", e);
                            }
                        }

                        @Override
                        public void onApplicationNotInstalled(String applicationId) {
                            handleAppNotInstalled(connectIQ, device);
                        }
                    });
                }
            } else {
                TextView infoText = findViewById(R.id.connect_infotext);
                infoText.setText(R.string.connect_no_device);
                CardView connectCard = findViewById(R.id.connect_card);
                connectCard.setCardBackgroundColor(0xffee7777);
            }
        } catch (InvalidStateException | ServiceUnavailableException e) {
            Log.error("Error getting connected devices", e);
        }
    }

    /** gimporter (app or widget) is on the watch: tapping the watch card manages it. */
    private void onWatchAppFound(ConnectIQ connectIQ, IQDevice device, IQApp app) {
        WatchLink.setConnection(connectIQ, device, app);
        findViewById(R.id.connect_card).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ManageWatchActivity.class)));
    }

    private void handleAppNotInstalled(ConnectIQ connectIQ, IQDevice device) {
        findViewById(R.id.connect_card).setOnClickListener(v -> {
            try {
                connectIQ.openStore(CONNECT_IQ_GIMPORTER_APP_STOREENTRY);
            } catch (InvalidStateException | ServiceUnavailableException e) {
                Log.error("Error opening store", e);
            }
        });

        try {
            connectIQ.getApplicationInfo(CONNECT_IQ_GIMPORTER_WIDGET, device, new ConnectIQ.IQApplicationInfoListener() {
                @Override
                public void onApplicationInfoReceived(IQApp app) {
                    TextView infoText = findViewById(R.id.connect_infotext);
                    infoText.setText(R.string.connect_app_only_widget_installed);
                    CardView connectCard = findViewById(R.id.connect_card);
                    connectCard.setCardBackgroundColor(0xff77cc77);

                    mConnectedDevice = device;
                    mConnectedApp = app;
                    onWatchAppFound(connectIQ, device, app);

                    Log.info("Garmin widget {} connected to device {}",
                            app.getApplicationId(), device.getDeviceIdentifier());
                    registerMessageListener(connectIQ, device, app);
                    // A widget cannot be opened from the phone; the location
                    // waits until the user opens it.
                    sendPendingLocation(device, app);
                }

                @Override
                public void onApplicationNotInstalled(String applicationId) {
                    TextView infoText = findViewById(R.id.connect_infotext);
                    infoText.setText(R.string.connect_app_not_installed);
                    CardView connectCard = findViewById(R.id.connect_card);
                    connectCard.setCardBackgroundColor(0xffee7777);
                }
            });
        } catch (InvalidStateException | ServiceUnavailableException e) {
            Log.error("Error checking widget", e);
        }
    }

    private void registerMessageListener(ConnectIQ connectIQ, IQDevice device, IQApp app) {
        long deviceId = device.getDeviceIdentifier();

        // Only register once per device
        if (mRegisteredDevices.contains(deviceId)) {
            Log.debug("Device {} already registered for app events", deviceId);
            return;
        }

        try {
            connectIQ.registerForAppEvents(device, app, (iqDevice, iqApp, message, status) -> {
                if (message instanceof ArrayList && !message.isEmpty()) {
                    Object firstElement = ((ArrayList<?>) message).get(0);
                    List<WatchContent.Item> content = WatchContent.parseContent(firstElement);
                    if (content != null) {
                        // for ManageWatchActivity
                        runOnUiThread(() -> WatchLink.dispatchContent(content));
                        return;
                    }
                    if (!(firstElement instanceof ArrayList)) {
                        return;
                    }
                    Object request = ((ArrayList<?>) firstElement).get(0);
                    if (!(request instanceof String)) {
                        return;
                    }
                    String msg = (String) request;
                    if ("GET_PORT".equals(msg)) {
                        sendPortResponse(iqDevice, iqApp);
                    }
                }
            });

            mRegisteredDevices.add(deviceId);
            Log.info("Registered message listener for Garmin app on device {}", deviceId);
        } catch (InvalidStateException | ServiceUnavailableException e) {
            Log.error("Error registering message listener for device {}", deviceId, e);
        }
    }

    /**
     * Send a shared location straight to the watch app and ask to open it;
     * the app saves the location and starts navigating as soon as it runs.
     * The dir.json listing stays as a fallback without a connection.
     */
    private void pushLocationToWatch(SharedLocation loc) {
        if (mConnectIQ == null || mConnectedDevice == null || mConnectedApp == null) {
            // launchIQApp() sends it once the watch app is known
            mPendingPush = loc;
            return;
        }
        mPendingPush = loc;
        sendPendingLocation(mConnectedDevice, mConnectedApp);
        try {
            mConnectIQ.openApplication(mConnectedDevice, mConnectedApp, (iqDevice, iqApp, status) ->
                    Log.info("Open app for location: {}", status));
        } catch (InvalidStateException | ServiceUnavailableException e) {
            Log.error("Error opening application", e);
        }
    }

    private void sendPendingLocation(IQDevice device, IQApp app) {
        SharedLocation loc = mPendingPush;
        if (loc == null || mConnectIQ == null) {
            return;
        }
        mPendingPush = null;
        try {
            mConnectIQ.sendMessage(device, app, loc.toWatchMessage(System.currentTimeMillis()),
                    (iqDevice, iqApp, status) -> {
                        if (status == ConnectIQ.IQMessageStatus.SUCCESS) {
                            Log.info("Sent location {} to the watch", loc.getName());
                        } else {
                            Log.error("Failed to send location to the watch: {}", status);
                        }
                    });
        } catch (InvalidStateException | ServiceUnavailableException e) {
            Log.error("Error sending location to the watch", e);
        }
    }

    private void sendPortResponse(IQDevice device, IQApp app) {
        if (mConnectIQ != null && server != null) {
            try {
                int port = server.getListeningPort();
                mConnectIQ.sendMessage(device, app, port, (iqDevice, iqApp, status) -> {
                    if (status == ConnectIQ.IQMessageStatus.SUCCESS) {
                        Log.info("Successfully sent port {} to Garmin app", port);
                    } else {
                        Log.error("Failed to send port to Garmin app: {}", status);
                    }
                });
            } catch (InvalidStateException | ServiceUnavailableException e) {
                Log.error("Error sending port response to Garmin app", e);
            }
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
        if (item.getItemId() == R.id.manage_watch) {
            // same as tapping the watch card; the screen explains if no watch is connected
            startActivity(new Intent(this, ManageWatchActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.help) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/gimportexportdevs/gexporter/wiki/Help"));
            startActivity(browserIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        if (mGpx2FitOptions == null) {
            return;
        }

        int id = v.getId();
        if (id == R.id.CBforceSpeed) {
            mGpx2FitOptions.setForceSpeed(mForceSpeed.isChecked());
        } else if (id == R.id.CBinject) {
            mGpx2FitOptions.setInjectCoursePoints(mInjectCoursePoints.isChecked());
        } else if (id == R.id.CBreducePoints) {
            if (mReducePoints.isChecked()) {
                String text = mMaxPoints.getText().toString();
                if (!text.isEmpty()) {
                    mGpx2FitOptions.setMaxPoints(Integer.parseInt(text));
                }
            } else {
                mGpx2FitOptions.setMaxPoints(0);
            }
        } else if (id == R.id.CBuse3D) {
            mGpx2FitOptions.setUse3dDistance(mUse3DDistance.isChecked());
        } else if (id == R.id.CBuseWalkingGrade) {
            mGpx2FitOptions.setWalkingGrade(mUseWalkingGrade.isChecked());
        } else if (id == R.id.file_button) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            openDocumentTreeLauncher.launch(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.debug("onResume called");

        // Keep a single options object so a running WebServer never holds a
        // stale reference; update it in place from persisted settings.
        if (mGpx2FitOptions == null) {
            mGpx2FitOptions = load();
        } else {
            mGpx2FitOptions.copyFrom(load());
        }
        updateUIFromOptions();
        processIntent(getIntent());

        if (server == null) {
            Log.debug("Server is null, checking permissions and serving");
            checkPermissionsAndServe();
        } else {
            Log.debug("Server already running");
        }
    }

    private void updateUIFromOptions() {
        setSpeedText(mGpx2FitOptions.getSpeedUnit());
        mForceSpeed.setChecked(mGpx2FitOptions.isForceSpeed());
        mUse3DDistance.setChecked(mGpx2FitOptions.isUse3dDistance());
        mInjectCoursePoints.setChecked(mGpx2FitOptions.isInjectCoursePoints());
        mUseWalkingGrade.setChecked(mGpx2FitOptions.isWalkingGrade());
        mReducePoints.setChecked(mGpx2FitOptions.getMaxPoints() > 0);

        int maxPoints = mGpx2FitOptions.getMaxPoints();
        if (maxPoints == 0) {
            maxPoints = 1000;
        }
        mMaxPoints.setText(String.format(Locale.getDefault(), "%d", maxPoints));
        mSpeedUnit.setSelection(mGpx2FitOptions.getSpeedUnit());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // singleTask: a new share is delivered here to the existing instance;
        // stash it so the next onResume processes it (and consumes it).
        setIntent(intent);
    }

    private void processIntent(Intent intent) {
        String action = intent.getAction();
        Log.debug("Processing intent: {}", intent);

        boolean handled = false;
        if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_VIEW.equals(action)
                || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            // A new share replaces the previous one.
            mSharedText = null;
            mSharedLocation = null;
            mPendingPush = null;
        }
        if (Intent.ACTION_SEND.equals(action)) {
            processSingleFile(intent);
            handled = true;
        } else if (Intent.ACTION_VIEW.equals(action)) {
            processViewFile(intent);
            handled = true;
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            processMultipleFiles(intent);
            handled = true;
        }

        if (!handled) {
            // Plain resume (no share) - do not tear down a running server.
            return;
        }

        // Consume the intent so a later resume doesn't reprocess the sticky
        // share and abort an in-flight download by rebuilding the server.
        setIntent(new Intent());

        if (server != null) {
            server.stop();
            server = null;
        }
    }

    private void processSingleFile(Intent intent) {
        Log.debug("ACTION_SEND");
        Uri uri = intent.getData();
        if (uri == null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                @SuppressWarnings("deprecation")
                Uri deprecatedUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                uri = deprecatedUri;
            }
        }
        if (uri == null) {
            // Some apps send styled text, which getStringExtra() drops.
            CharSequence extraText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            String text = extraText != null ? extraText.toString() : null;
            if (text != null) {
                Uri textUri = Uri.parse(text.trim());
                if ("content".equals(textUri.getScheme()) || "file".equals(textUri.getScheme())) {
                    uri = textUri;
                } else {
                    // A location shared from a map app (link, geo: URI or
                    // coordinates). The subject goes last: the name is taken
                    // from the first plain line, and subjects are often
                    // phrases like "Stephansdom in Organic Maps".
                    CharSequence subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT);
                    mSharedText = subject != null ? text + "\n" + subject : text;
                    mUris = new ArrayList<>();
                    Log.debug("Shared text: {}", mSharedText);
                    return;
                }
            }
        }

        if (uri != null) {
            Log.debug("URI {}: type {} scheme {}", uri, intent.getType(), uri.getScheme());
            mUris = new ArrayList<>();
            mUris.add(uri);
        }
    }

    private void processViewFile(Intent intent) {
        Log.debug("ACTION_VIEW");
        Uri uri = intent.getData();
        if (uri != null) {
            Log.debug("URI {}: type '{}'", uri, intent.getType());
            mUris = new ArrayList<>();
            if ("geo".equals(uri.getScheme())) {
                mSharedText = uri.toString();
            } else {
                mUris.add(uri);
            }
        }
    }

    private void processMultipleFiles(Intent intent) {
        Log.debug("ACTION_SEND_MULTIPLE");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            mUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
        } else {
            @SuppressWarnings("deprecation")
            ArrayList<Uri> deprecatedUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            mUris = deprecatedUris;
        }
    }

    private void checkPermissionsAndServe() {
        Log.debug("checkPermissionsAndServe called");

        // For Android 13+ (API 33+), READ_EXTERNAL_STORAGE is largely deprecated
        // We'll rely on the Downloads directory and intent-based file sharing
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Log.info("Android 13+, skipping permission check");
            serveFiles();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            Log.info("Permission not granted, requesting permission");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
        } else {
            Log.info("Permission granted, calling serveFiles");
            serveFiles();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.debug("onRequestPermissionsResult called with requestCode: {}", requestCode);
        if (requestCode == MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.info("Permission granted in callback, calling serveFiles");
                serveFiles();
            } else {
                Log.warn("Permission denied in callback");
                mTextView.setText(R.string.no_permission);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        save(mGpx2FitOptions);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (server != null) {
            server.stop();
            server = null;
            clearTempDir();
        }
    }

    @Override
    protected void onDestroy() {
        // Symmetric teardown for initConnectIQ()/registerForAppEvents(): without
        // it the ConnectIQ singleton keeps this (destroyed) activity reachable
        // through its listener map, and GET_PORT can be delivered to a dead
        // instance after a config-change recreation.
        if (mConnectIQ != null) {
            try {
                mConnectIQ.unregisterAllForEvents();
            } catch (Exception e) {
                Log.error("Error unregistering ConnectIQ events", e);
            }
            try {
                mConnectIQ.shutdown(this);
            } catch (Exception e) {
                Log.error("Error shutting down ConnectIQ", e);
            }
        }
        mRegisteredDevices.clear();
        WatchLink.clear();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK) {
            return;
        }

        if (requestCode == GEXPORTER_OPEN_DIR && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                mUris = new ArrayList<>();
                mUris.add(uri);
            }
        }
    }

    protected void serveFiles() {
        Log.debug("serveFiles called");
        String rootDirectoryUserDisplayable;
        File directory;

        if (mUris == null) {
            directory = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS);
            rootDirectoryUserDisplayable = directory.getAbsolutePath();
            Log.info("Using DIRECTORY_DOWNLOADS: {}", rootDirectoryUserDisplayable);
        } else {
            clearTempDir();

            mDirectory = new File(getCacheDir(), Long.toString(System.nanoTime()));

            boolean _mkdir = mDirectory.mkdir();

            directory = mDirectory;
            rootDirectoryUserDisplayable = mUris.isEmpty() ? "unknown" : formatUriForUserDisplay(mUris.get(0));
            if (mUris.size() > 1) {
                rootDirectoryUserDisplayable += " and others";
            }
            Log.info("OpenDir {}", mDirectory.getAbsolutePath());

            processUris();
        }

        startWebServer(directory);
        if (mSharedText != null && mDirectory != null) {
            serveSharedLocation(mDirectory);
        } else {
            displayFileList(directory, rootDirectoryUserDisplayable);
        }
    }

    /**
     * Turn the shared text into a one-waypoint GPX in the served directory.
     * Short links need the network, so resolving runs off the UI thread; the
     * server is already up and lists the file as soon as it exists.
     */
    private void serveSharedLocation(File directory) {
        if (mSharedLocation != null) {
            writeSharedLocation(directory, mSharedLocation);
            return;
        }

        mTextView.setText(R.string.resolving_location);
        final String text = mSharedText;
        new Thread(() -> {
            SharedLocation loc = SharedLocation.resolve(text, new SharedLocation.HttpFetcher());
            runOnUiThread(() -> {
                if (isDestroyed() || !Objects.equals(text, mSharedText)) {
                    return;  // superseded by a newer share
                }
                if (loc == null) {
                    mTextView.setText(R.string.location_not_found);
                    return;
                }
                mSharedLocation = loc;
                pushLocationToWatch(loc);
                // onStop() may have cleared the directory meanwhile; the next
                // serveFiles() writes the cached location into the new one.
                if (directory.equals(mDirectory)) {
                    writeSharedLocation(directory, loc);
                }
            });
        }, "resolve-location").start();
    }

    private void writeSharedLocation(File directory, SharedLocation loc) {
        File file = new File(directory, loc.fileName());
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(loc.toGpx().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.error("Could not write {}", file, e);
        }
        Log.info("Serving location {} at {}, {}", loc.getName(), loc.getLat(), loc.getLon());
        displayFileList(directory, loc.getName());
    }

    private void processUris() {
        for (Uri uri : mUris) {
            String scheme = uri.getScheme();

            if (!("content".equals(scheme) || "file".equals(scheme))) {
                Log.debug("Skip URI {} scheme {}", uri, scheme);
                continue;
            }

            DocumentFile treeFile = null;
            try {
                treeFile = DocumentFile.fromTreeUri(getApplicationContext(), uri);
            } catch (Exception e) {
                Log.error("Cannot create TreeUri", e);
            }

            if (treeFile != null && treeFile.isDirectory()) {
                processDirectory(treeFile);
            } else {
                processFile(uri);
            }
        }
    }

    private void processDirectory(DocumentFile treeFile) {
        for (DocumentFile doc : treeFile.listFiles()) {
            if (!doc.isFile()) {
                continue;
            }
            try {
                String name = FileTypes.safeFileName(getFileName(doc.getUri()));
                if (isValidFileType(name)) {
                    copyInputStreamToFile(mCR.openInputStream(doc.getUri()), name);
                }
            } catch (Exception e) {
                // A provider may refuse access (SecurityException) as well.
                Log.error("Exception processing file", e);
            }
        }
    }

    private void processFile(Uri uri) {
        Log.debug("Open URI {} scheme {}", uri, uri.getScheme());
        try {
            String name = FileTypes.safeFileName(getFileName(uri));
            copyInputStreamToFile(mCR.openInputStream(uri), name);
        } catch (Exception e) {
            // A provider may refuse access (SecurityException) as well.
            Log.error("Exception opening URI", e);
        }
    }

    private boolean isValidFileType(String filename) {
        return FileTypes.isTrackFile(filename);
    }

    private void startWebServer(File directory) {
        Log.debug("Starting web server for directory: {}", directory.getAbsolutePath());
        try {
            server = new WebServer(directory, getCacheDir(), 22222, mGpx2FitOptions);
            server.start();
            int actualPort = server.getListeningPort();
            Log.info("Web server initialized on port: {}", actualPort);
        } catch (IOException | NoSuchAlgorithmException e) {
            Log.warn("Failed to start server on port 22222, trying dynamic port: {}", e.toString());

            try {
                server = new WebServer(directory, getCacheDir(), 0, mGpx2FitOptions);
                server.start();
                int actualPort = server.getListeningPort();
                Log.info("Web server initialized on dynamic port: {}", actualPort);
            } catch (IOException | NoSuchAlgorithmException e2) {
                Log.error("The server could not start on any port: {}", e2.toString());
                // Don't leave a dead, never-started instance in `server`: it
                // would block every retry and make GET_PORT reply -1.
                server = null;
                mTextView.setText(R.string.no_server);
            }
        }
    }

    private void displayFileList(File directory, String rootDirectoryUserDisplayable) {
        FilenameFilter filenameFilter = (dir, name) -> isValidFileType(name);
        String[] fileList = directory.list(filenameFilter);

        if (fileList == null || fileList.length == 0) {
            mTextView.setText(mDirectory == null ? getString(R.string.serving_not_possible) :
                    getString(R.string.no_files_to_serve, rootDirectoryUserDisplayable));
        } else {
            Arrays.sort(fileList);
            mTextView.setText(String.format(getResources().getString(R.string.serving_from),
                    rootDirectoryUserDisplayable, TextUtils.join("\n", fileList)));
        }
    }

    /**
     * Copy a shared file into the served directory. A name without a .gpx or
     * .fit extension gets one from the file's first bytes; anything that is
     * neither GPX nor FIT is skipped.
     */
    private void copyInputStreamToFile(InputStream in, String name) {
        if (in == null) {
            return;
        }
        try (in) {
            byte[] buf = new byte[1024];
            int len = readFully(in, buf);

            if (!isValidFileType(name)) {
                String ext = FileTypes.sniffExtension(buf, len);
                if (ext == null) {
                    Log.warn("Skipping {}: neither GPX nor FIT", name);
                    return;
                }
                name = name + ext;
            }

            try (OutputStream out = new FileOutputStream(new File(mDirectory, name))) {
                while (len > 0) {
                    out.write(buf, 0, len);
                    len = in.read(buf);
                }
            }
        } catch (IOException e) {
            Log.error("copyInputStreamToFile {}: {}", name, e.toString());
        }
    }

    /** Read until buf is full or the stream ends; a single read() may return less. */
    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int n = in.read(buf, total, buf.length - total);
            if (n < 0) {
                break;
            }
            total += n;
        }
        return total;
    }

    public String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
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
            if (result == null) {
                return null;
            }
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private String formatUriForUserDisplay(Uri uri) {
        String[] pathParts = Objects.requireNonNull(uri.getPath()).split(":");
        String displayString = pathParts[pathParts.length - 1];
        if (!displayString.startsWith("/")) {
            displayString = "/" + displayString;
        }
        return displayString;
    }

    private void setSpeedText(int pos) {
        double speed = mGpx2FitOptions.getSpeed();
        if (Double.isNaN(speed)) {
            speed = 10.0;
        }

        String val = null;
        switch (pos) {
            case 0: // min/km
                speed = 1000.0 / speed / 60.0;
                if (speed < 60) {
                    val = String.format(Locale.getDefault(), "%d:%02d", (int) speed, ((int) (speed * 60.0 + 0.5) % 60));
                } else {
                    val = String.format(Locale.getDefault(), "%d:%02d:%02d", ((int) speed) / 60,
                            ((int) speed % 60), ((int) (speed * 60.0 + 0.5) % 60));
                }
                break;
            case 1: // km/h
                speed = speed / 1000.0 * 3600.0;
                val = String.format(Locale.getDefault(), "%.2f", speed);
                break;
            case 2: // min/mi
                speed = 1609.344 / speed / 60.0;
                if (speed < 60) {
                    val = String.format(Locale.getDefault(), "%d:%02d", (int) speed, ((int) (speed * 60.0 + 0.5) % 60));
                } else {
                    val = String.format(Locale.getDefault(), "%d:%02d:%02d", ((int) speed) / 60,
                            ((int) speed % 60), ((int) (speed * 60.0 + 0.5) % 60));
                }
                break;
            case 3: // mph
                speed = speed / 1609.344 * 3600.0;
                val = String.format(Locale.getDefault(), "%.2f", speed);
                break;
        }

        if (mSpeed != null && val != null) {
            mSpeed.setText(val);
        }
    }

    private void clearTempDir() {
        if (mDirectory != null) {
            try {
                rmdir(mDirectory);
            } catch (IOException e) {
                Log.error("Failed to delete directory {}: {}", mDirectory.getAbsolutePath(), e.getMessage());
            } finally {
                mDirectory = null;
            }
        }
    }

    private void rmdir(File f) throws IOException {
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File c : files) {
                    rmdir(c);
                }
            }
        }
        if (!f.delete()) {
            throw new FileNotFoundException("Failed to delete file: " + f);
        }
    }

    public void save(Gpx2FitOptions options) {
        Application app = getApplication();
        SharedPreferences prefs = app.getSharedPreferences(app.getApplicationInfo().name, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();
        editor.putString(options.getClass().getName(), gson.toJson(options));
        editor.apply();
    }

    public Gpx2FitOptions load() {
        Application app = getApplication();
        Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
        SharedPreferences prefs = app.getSharedPreferences(app.getApplicationInfo().name, Context.MODE_PRIVATE);
        String json = prefs.getString(Gpx2FitOptions.class.getName(), null);
        Gpx2FitOptions opts = null;
        if (json != null && !json.isEmpty()) {
            opts = gson.fromJson(JsonParser.parseString(json).getAsJsonObject(), Gpx2FitOptions.class);
        }
        return opts != null ? opts : new Gpx2FitOptions();
    }

    private class SpeedTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
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

                    Number parsedValue = mNumberFormat.parse(as[0]);
                    if (parsedValue == null) {
                        throw new ParseException("Cannot parse speed value", 0);
                    }
                    speed = parsedValue.doubleValue();

                    if (as.length > 1) {
                        Number parsedValue1 = mNumberFormat.parse(as[1]);
                        if (parsedValue1 == null) {
                            throw new ParseException("Cannot parse speed value", 0);
                        }
                        speed = parsedValue1.doubleValue() + speed / 60.0;
                    }

                    if (as.length > 2) {
                        Number parsedValue2 = mNumberFormat.parse(as[2]);
                        if (parsedValue2 == null) {
                            throw new ParseException("Cannot parse speed value", 0);
                        }
                        speed = parsedValue2.doubleValue() * 60.0 + speed;
                    }
                } catch (ParseException e) {
                    speed = 0.0;
                }

                if (speed > 0.0) {
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
                            break;
                    }
                }
                mGpx2FitOptions.setSpeed(speed);
            }
        }
    }

    private class MaxPointsTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            if (editable.length() > 0 && mGpx2FitOptions != null && mReducePoints.isChecked()) {
                try {
                    mGpx2FitOptions.setMaxPoints(Integer.parseInt(editable.toString()));
                } catch (NumberFormatException e) {
                    Log.error("Invalid max points value");
                }
            }
        }
    }

}