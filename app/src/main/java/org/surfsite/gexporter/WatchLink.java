package org.surfsite.gexporter;

import com.garmin.android.connectiq.ConnectIQ;
import com.garmin.android.connectiq.IQApp;
import com.garmin.android.connectiq.IQDevice;
import com.garmin.android.connectiq.exception.InvalidStateException;
import com.garmin.android.connectiq.exception.ServiceUnavailableException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The ConnectIQ connection to gimporter on the watch, shared between
 * MainActivity (which owns the SDK and receives the watch's messages) and
 * ManageWatchActivity. Main thread only.
 *
 * gimporter exists as a device app and as a widget. The watch treats them
 * as separate apps: each one lists and deletes only the content it
 * downloaded itself, so both are asked.
 */
final class WatchLink {
    private static final Logger Log = LoggerFactory.getLogger(WatchLink.class);

    interface Listener {
        /** @param owner the {@link #ownerOf app id} of the app or widget that answered */
        void onWatchContent(String owner, List<WatchContent.Item> items);

        /** gimporter reported what it is doing */
        default void onWatchStatus(WatchContent.Status status) {
        }
    }

    // A download on the watch normally takes seconds; a missing "started" or
    // "failed" (app killed, connection lost) must not block sending forever.
    private static final long BUSY_TIMEOUT_MS = 60000;
    private static WatchContent.Status sStatus;
    private static long sStatusTime;

    private static ConnectIQ sConnectIQ;
    private static IQDevice sDevice;
    private static IQApp sApp;
    private static IQApp sWidget;
    private static final List<Listener> sListeners = new ArrayList<>();
    // the latest reply of app and widget, for marking what is on the watch
    private static final Map<String, List<WatchContent.Item>> sLatest = new LinkedHashMap<>();

    private WatchLink() {
    }

    /** App ids come with or without dashes depending on the source. */
    static String ownerOf(String applicationId) {
        return applicationId == null ? "" : applicationId.replace("-", "").toLowerCase(Locale.ROOT);
    }

    static String ownerOf(IQApp app) {
        return ownerOf(app.getApplicationId());
    }

    static void addApp(ConnectIQ connectIQ, IQDevice device, IQApp app, boolean widget) {
        sConnectIQ = connectIQ;
        sDevice = device;
        if (widget) {
            sWidget = app;
        } else {
            sApp = app;
        }
    }

    static void clear() {
        sConnectIQ = null;
        sDevice = null;
        sApp = null;
        sWidget = null;
        sLatest.clear();
        sStatus = null;
    }

    static boolean isConnected() {
        return sConnectIQ != null && sDevice != null && (sApp != null || sWidget != null);
    }

    static boolean hasWidget() {
        return sWidget != null;
    }

    static boolean isWidget(String owner) {
        return sWidget != null && ownerOf(sWidget).equals(owner);
    }

    static void addListener(Listener listener) {
        if (!sListeners.contains(listener)) {
            sListeners.add(listener);
        }
    }

    static void removeListener(Listener listener) {
        sListeners.remove(listener);
    }

    /** Called by MainActivity for every "content" reply from app or widget. */
    static void dispatchContent(String owner, List<WatchContent.Item> items) {
        sLatest.put(owner, items);
        for (Listener listener : new ArrayList<>(sListeners)) {
            listener.onWatchContent(owner, items);
        }
    }

    /** Called by MainActivity for every "status" message from app or widget. */
    static void dispatchStatus(WatchContent.Status status, long nowMillis) {
        sStatus = status;
        sStatusTime = nowMillis;
        for (Listener listener : new ArrayList<>(sListeners)) {
            listener.onWatchStatus(status);
        }
    }

    /**
     * gimporter is downloading a track: another track or location sent now
     * would arrive while it finishes and then leaves the app.
     * @return the title being imported, or null if not busy
     */
    static String busyWith(long nowMillis) {
        WatchContent.Status status = sStatus;
        if (status != null && "importing".equals(status.state) && nowMillis - sStatusTime < BUSY_TIMEOUT_MS) {
            return status.title;
        }
        return null;
    }

    /** Everything app and widget reported last; empty until one answered. */
    static List<WatchContent.Item> latestContent() {
        List<WatchContent.Item> all = new ArrayList<>();
        for (List<WatchContent.Item> items : sLatest.values()) {
            all.addAll(items);
        }
        return all;
    }

    /**
     * Send to the app, or to the widget if there is no app. For requests that
     * make the watch do something now: only the app can be opened from here.
     */
    static boolean sendToPreferred(Map<String, Object> message) {
        IQApp app = sApp != null ? sApp : sWidget;
        return app != null && send(app, message);
    }

    /** Send to both app and widget. @return false if there is no watch to send to */
    static boolean sendToAll(Map<String, Object> message) {
        boolean sent = false;
        for (IQApp app : new IQApp[]{sApp, sWidget}) {
            if (app != null) {
                sent |= send(app, message);
            }
        }
        return sent;
    }

    /** Send to the app or widget that owns some content. */
    static boolean send(String owner, Map<String, Object> message) {
        for (IQApp app : new IQApp[]{sApp, sWidget}) {
            if (app != null && ownerOf(app).equals(owner)) {
                return send(app, message);
            }
        }
        return false;
    }

    private static boolean send(IQApp app, Map<String, Object> message) {
        if (sConnectIQ == null || sDevice == null) {
            return false;
        }
        try {
            sConnectIQ.sendMessage(sDevice, app, message, (device, a, status) ->
                    Log.info("Watch request {} to {}: {}", message.get("type"), a.getApplicationId(), status));
            return true;
        } catch (InvalidStateException | ServiceUnavailableException e) {
            Log.error("Error sending to the watch", e);
            return false;
        }
    }

    /**
     * Ask the watch to open the gimporter app ("open gimporter?" unless it
     * runs). A widget cannot be opened from the phone.
     */
    static void openApp() {
        if (sConnectIQ == null || sDevice == null || sApp == null) {
            return;
        }
        try {
            sConnectIQ.openApplication(sDevice, sApp, (device, app, status) ->
                    Log.info("Open app: {}", status));
        } catch (InvalidStateException | ServiceUnavailableException e) {
            Log.error("Error opening application", e);
        }
    }
}
