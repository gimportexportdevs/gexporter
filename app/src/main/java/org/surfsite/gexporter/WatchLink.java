package org.surfsite.gexporter;

import com.garmin.android.connectiq.ConnectIQ;
import com.garmin.android.connectiq.IQApp;
import com.garmin.android.connectiq.IQDevice;
import com.garmin.android.connectiq.exception.InvalidStateException;
import com.garmin.android.connectiq.exception.ServiceUnavailableException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * The ConnectIQ connection to gimporter on the watch, shared between
 * MainActivity (which owns the SDK and receives the watch's messages) and
 * ManageWatchActivity. Main thread only.
 */
final class WatchLink {
    private static final Logger Log = LoggerFactory.getLogger(WatchLink.class);

    interface Listener {
        void onWatchContent(List<WatchContent.Item> items);
    }

    private static ConnectIQ sConnectIQ;
    private static IQDevice sDevice;
    private static IQApp sApp;
    private static Listener sListener;

    private WatchLink() {
    }

    static void setConnection(ConnectIQ connectIQ, IQDevice device, IQApp app) {
        sConnectIQ = connectIQ;
        sDevice = device;
        sApp = app;
    }

    static void clear() {
        setConnection(null, null, null);
    }

    static boolean isConnected() {
        return sConnectIQ != null && sDevice != null && sApp != null;
    }

    static void setListener(Listener listener) {
        sListener = listener;
    }

    /** Called by MainActivity for every "content" reply from the watch. */
    static void dispatchContent(List<WatchContent.Item> items) {
        if (sListener != null) {
            sListener.onWatchContent(items);
        }
    }

    /** @return false if there is no watch to send to */
    static boolean send(Map<String, Object> message) {
        if (!isConnected()) {
            return false;
        }
        try {
            sConnectIQ.sendMessage(sDevice, sApp, message, (device, app, status) ->
                    Log.info("Watch request {}: {}", message.get("type"), status));
            return true;
        } catch (InvalidStateException | ServiceUnavailableException e) {
            Log.error("Error sending to the watch", e);
            return false;
        }
    }

    /** Ask the watch to open gimporter ("open gimporter?" unless it runs). */
    static void openApp() {
        if (!isConnected()) {
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
