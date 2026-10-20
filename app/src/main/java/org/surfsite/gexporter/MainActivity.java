package org.surfsite.gexporter;

import static android.os.Environment.DIRECTORY_DOWNLOADS;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.garmin.android.connectiq.ConnectIQ;
import com.garmin.android.connectiq.IQApp;
import com.garmin.android.connectiq.IQDevice;
import com.garmin.android.connectiq.exception.InvalidStateException;
import com.garmin.android.connectiq.exception.ServiceUnavailableException;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final Logger Log = LoggerFactory.getLogger(MainActivity.class);
    private static final int GEXPORTER_OPEN_DIR = 1;
    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 300;

    private static final String CONNECT_IQ_GIMPORTER_APP = "9B0A09CF-C89E-4F7C-A5E4-AB21400EE424";
    private static final String CONNECT_IQ_GIMPORTER_APP_STOREENTRY = "DE11ADC4-FDBB-40B5-86AC-7F93B47EA5BB";
    private static final String CONNECT_IQ_GIMPORTER_WIDGET = "B5FD4C5F-E0F8-48E8-8A03-E37E86971CEB";

    @Nullable
    private WebServer server = null;
    private TextView mStatusText;
    private TextView mServedFrom;
    private View mEmptyView;
    // what the watch can fetch, listed on the main screen
    private final List<ServedFiles.Entry> mServed = new ArrayList<>();
    private final ServedAdapter mServedAdapter = new ServedAdapter();
    private final ExecutorService mScanner = Executors.newSingleThreadExecutor();
    private File mServedDir;
    private Gpx2FitOptions mGpx2FitOptions = null;
    private File mDirectory = null;
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
    // "device:app" pairs with a message listener; app and widget each need one
    private final Set<String> mRegisteredApps = new HashSet<>();
    
    private ActivityResultLauncher<Intent> openDocumentTreeLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Draw behind the system bars, with icon colors matching the theme
        EdgeToEdge.enable(this);
        
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
        mStatusText = findViewById(R.id.status_text);
        mServedFrom = findViewById(R.id.served_from);
        mEmptyView = findViewById(R.id.served_empty);

        RecyclerView list = findViewById(R.id.served_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(mServedAdapter);
        new ItemTouchHelper(new SwipeToRemove()).attachToRecyclerView(list);

        findViewById(R.id.choose_folder).setOnClickListener(v ->
                openDocumentTreeLauncher.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)));
        setWatchState(WatchState.CONNECTING, null);
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
                setWatchState(WatchState.INIT_FAILED, null);
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
                            WatchLink.addApp(connectIQ, device, app, false);
                            setWatchState(WatchState.CONNECTED, device);

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
                                connectIQ.openApplication(device, app, (iqDevice, iqApp, status) ->
                                        Log.info("Garmin app {} on device {}: {}", app.getApplicationId(),
                                                device.getDeviceIdentifier(), status));
                            } catch (InvalidStateException | ServiceUnavailableException e) {
                                Log.error("Error opening application", e);
                            }
                            // The widget keeps its own tracks; it is managed too.
                            checkWidget(connectIQ, device, true);
                        }

                        @Override
                        public void onApplicationNotInstalled(String applicationId) {
                            checkWidget(connectIQ, device, false);
                        }
                    });
                }
            } else {
                setWatchState(WatchState.NO_DEVICE, null);
            }
        } catch (InvalidStateException | ServiceUnavailableException e) {
            Log.error("Error getting connected devices", e);
        }
    }

    /**
     * Look for the gimporter widget as well: it downloads and owns content of
     * its own. Without the app, it is also what locations and port replies go
     * to.
     */
    private void checkWidget(ConnectIQ connectIQ, IQDevice device, boolean appInstalled) {
        try {
            connectIQ.getApplicationInfo(CONNECT_IQ_GIMPORTER_WIDGET, device, new ConnectIQ.IQApplicationInfoListener() {
                @Override
                public void onApplicationInfoReceived(IQApp widget) {
                    Log.info("Garmin widget {} on device {}", widget.getApplicationId(), device.getDeviceIdentifier());
                    WatchLink.addApp(connectIQ, device, widget, true);
                    registerMessageListener(connectIQ, device, widget);
                    if (!appInstalled) {
                        mConnectedDevice = device;
                        mConnectedApp = widget;
                        setWatchState(WatchState.WIDGET_ONLY, device);
                        // A widget cannot be opened from the phone; the location
                        // waits until the user opens it.
                        sendPendingLocation(device, widget);
                    }
                }

                @Override
                public void onApplicationNotInstalled(String applicationId) {
                    if (!appInstalled) {
                        setWatchState(WatchState.NOT_INSTALLED, device);
                    }
                }
            });
        } catch (InvalidStateException | ServiceUnavailableException e) {
            Log.error("Error checking widget", e);
        }
    }

    private enum WatchState { CONNECTING, NO_DEVICE, INIT_FAILED, NOT_INSTALLED, WIDGET_ONLY, CONNECTED }

    /** Show the watch, its connection state and the action that fits it. */
    private void setWatchState(WatchState state, @Nullable IQDevice device) {
        MaterialCardView card = findViewById(R.id.watch_card);
        TextView name = findViewById(R.id.watch_name);
        TextView status = findViewById(R.id.watch_status);
        ImageView icon = findViewById(R.id.watch_icon);
        MaterialButton action = findViewById(R.id.watch_action);

        if (device != null && device.getFriendlyName() != null && !device.getFriendlyName().isEmpty()) {
            name.setText(device.getFriendlyName());
        }
        int container = com.google.android.material.R.attr.colorSurfaceContainerHighest;
        int onContainer = com.google.android.material.R.attr.colorOnSurface;
        action.setVisibility(View.GONE);
        switch (state) {
            case CONNECTING:
                status.setText(R.string.connect_ongoing);
                break;
            case NO_DEVICE:
            case INIT_FAILED:
                status.setText(state == WatchState.NO_DEVICE ? R.string.connect_no_device : R.string.connect_init_failed);
                container = com.google.android.material.R.attr.colorErrorContainer;
                onContainer = com.google.android.material.R.attr.colorOnErrorContainer;
                break;
            case NOT_INSTALLED:
                status.setText(R.string.watch_status_not_installed);
                container = com.google.android.material.R.attr.colorErrorContainer;
                onContainer = com.google.android.material.R.attr.colorOnErrorContainer;
                action.setText(R.string.install);
                action.setOnClickListener(v -> openStore());
                action.setVisibility(View.VISIBLE);
                break;
            case WIDGET_ONLY:
            case CONNECTED:
                status.setText(state == WatchState.CONNECTED ? R.string.connect_connected : R.string.watch_status_widget_only);
                container = com.google.android.material.R.attr.colorPrimaryContainer;
                onContainer = com.google.android.material.R.attr.colorOnPrimaryContainer;
                action.setText(R.string.manage);
                action.setOnClickListener(v -> startActivity(new Intent(this, ManageWatchActivity.class)));
                action.setVisibility(View.VISIBLE);
                break;
        }
        int fg = MaterialColors.getColor(card, onContainer);
        card.setCardBackgroundColor(MaterialColors.getColor(card, container));
        name.setTextColor(fg);
        status.setTextColor(fg);
        icon.setColorFilter(fg);
    }

    private void openStore() {
        if (mConnectIQ == null) {
            return;
        }
        try {
            mConnectIQ.openStore(CONNECT_IQ_GIMPORTER_APP_STOREENTRY);
        } catch (InvalidStateException | ServiceUnavailableException e) {
            Log.error("Error opening store", e);
        }
    }

    private void registerMessageListener(ConnectIQ connectIQ, IQDevice device, IQApp app) {
        long deviceId = device.getDeviceIdentifier();
        String key = deviceId + ":" + WatchLink.ownerOf(app);

        // Only register once per device and app
        if (!mRegisteredApps.add(key)) {
            Log.debug("{} already registered for app events", key);
            return;
        }

        try {
            connectIQ.registerForAppEvents(device, app, (iqDevice, iqApp, message, status) -> {
                if (message instanceof ArrayList && !message.isEmpty()) {
                    Object firstElement = ((ArrayList<?>) message).get(0);
                    String owner = WatchLink.ownerOf(iqApp);
                    List<WatchContent.Item> content = WatchContent.parseContent(firstElement, owner);
                    if (content != null) {
                        Log.info("Watch content from {}: {} items", iqApp.getApplicationId(), content.size());
                        // for ManageWatchActivity
                        runOnUiThread(() -> WatchLink.dispatchContent(owner, content));
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

            Log.info("Registered message listener for {}", key);
        } catch (InvalidStateException | ServiceUnavailableException e) {
            mRegisteredApps.remove(key);
            Log.error("Error registering message listener for {}", key, e);
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
        if (item.getItemId() == R.id.settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
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
    protected void onResume() {
        super.onResume();
        Log.debug("onResume called");

        // Keep a single options object so a running WebServer never holds a
        // stale reference; update it in place from persisted settings.
        if (mGpx2FitOptions == null) {
            mGpx2FitOptions = OptionsStore.load(this);
        } else {
            mGpx2FitOptions.copyFrom(OptionsStore.load(this));
        }
        processIntent(getIntent());

        if (server == null) {
            Log.debug("Server is null, checking permissions and serving");
            checkPermissionsAndServe();
        } else {
            Log.debug("Server already running");
        }
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
                setStatus(R.string.no_permission);
            }
        }
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
        mRegisteredApps.clear();
        WatchLink.clear();
        mScanner.shutdownNow();
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
        setStatus(0);
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

        setStatus(R.string.resolving_location);
        final String text = mSharedText;
        new Thread(() -> {
            SharedLocation loc = SharedLocation.resolve(text, new SharedLocation.HttpFetcher());
            runOnUiThread(() -> {
                if (isDestroyed() || !Objects.equals(text, mSharedText)) {
                    return;  // superseded by a newer share
                }
                if (loc == null) {
                    setStatus(R.string.location_not_found);
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
        setStatus(0);
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
                setStatus(R.string.no_server);
            }
        }
    }

    /** List what the watch can fetch; GPX files are parsed in the background. */
    private void displayFileList(File directory, String rootDirectoryUserDisplayable) {
        mServedDir = directory;
        mServedFrom.setText(getString(R.string.served_from, rootDirectoryUserDisplayable));
        mScanner.execute(() -> {
            List<ServedFiles.Entry> entries = ServedFiles.scan(directory);
            runOnUiThread(() -> {
                if (isDestroyed() || !directory.equals(mServedDir)) {
                    return;  // a newer share or folder replaced it
                }
                showServed(entries);
            });
        });
    }

    @SuppressLint("NotifyDataSetChanged")  // every scan replaces the whole list
    private void showServed(List<ServedFiles.Entry> entries) {
        mServed.clear();
        mServed.addAll(entries);
        mServedAdapter.notifyDataSetChanged();
        mEmptyView.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** A line under the list title for progress and problems; 0 hides it. */
    private void setStatus(int text) {
        if (text == 0) {
            mStatusText.setVisibility(View.GONE);
        } else {
            mStatusText.setText(text);
            mStatusText.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Swiping removes an entry from what is offered to the watch. Only for
     * gexporter's own copies of shared files; files in the Downloads folder
     * belong to the user and are never deleted.
     */
    private class SwipeToRemove extends ItemTouchHelper.SimpleCallback {
        SwipeToRemove() {
            super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        }

        @Override
        public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            return mServedDir != null && mServedDir.equals(mDirectory) ? super.getSwipeDirs(rv, vh) : 0;
        }

        @Override
        public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                              @NonNull RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
            int pos = vh.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                return;
            }
            ServedFiles.Entry entry = mServed.get(pos);
            File dir = mServedDir;
            // a GPX file with several locations goes as a whole
            List<ServedFiles.Entry> removed = new ArrayList<>();
            for (ServedFiles.Entry e : mServed) {
                if (e.file.equals(entry.file)) {
                    removed.add(e);
                }
            }
            List<ServedFiles.Entry> remaining = new ArrayList<>(mServed);
            remaining.removeAll(removed);
            showServed(remaining);
            Snackbar.make(findViewById(R.id.main_root), getString(R.string.removed_one, entry.name), Snackbar.LENGTH_LONG)
                    .setAnchorView(R.id.choose_folder)
                    .setAction(R.string.undo, v -> displayFileList(dir, String.valueOf(mServedFrom.getText())))
                    .addCallback(new Snackbar.Callback() {
                        @Override
                        public void onDismissed(Snackbar bar, int event) {
                            if (event != DISMISS_EVENT_ACTION && !entry.file.delete()) {
                                Log.warn("Could not remove {}", entry.file);
                            }
                        }
                    })
                    .show();
        }
    }

    private class ServedAdapter extends RecyclerView.Adapter<ServedAdapter.Holder> {
        class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView name;
            final TextView detail;

            Holder(View v) {
                super(v);
                icon = v.findViewById(R.id.item_icon);
                name = v.findViewById(R.id.item_name);
                detail = v.findViewById(R.id.item_kind);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_watch_content, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            ServedFiles.Entry entry = mServed.get(position);
            h.icon.setImageResource(entry.location ? R.drawable.ic_location : R.drawable.ic_track);
            h.name.setText(entry.name);
            if (entry.location) {
                h.detail.setText(R.string.kind_location);
            } else if (entry.length != ServedFiles.NO_LENGTH) {
                h.detail.setText(String.format(Locale.getDefault(), "%.1f km", entry.length / 1000.0));
            } else {
                h.detail.setText(entry.file.getName().toLowerCase(Locale.ROOT).endsWith(".fit") ? "FIT" : "GPX");
            }
        }

        @Override
        public int getItemCount() {
            return mServed.size();
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
}
