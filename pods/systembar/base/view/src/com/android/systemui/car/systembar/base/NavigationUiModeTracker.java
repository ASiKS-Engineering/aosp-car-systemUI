package com.android.systemui.car.systembar.base;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserHandle;
import android.util.Log;

/** Remembers the last navigation UI mode (HOME / FULLSCREEN) announced by the launcher. */
final class NavigationUiModeTracker {
    private static final String TAG = "NavigationUiModeTracker";

    static final String ACTION_MODE_CHANGED =
            "com.example.campernavigator.action.NAVIGATION_UI_MODE_CHANGED";
    static final String EXTRA_MODE = "com.example.campernavigator.extra.NAVIGATION_UI_MODE";

    private static volatile String sMode;
    private static boolean sRegistered;

    private NavigationUiModeTracker() {}

    /** Last announced mode, or null while unknown. */
    static String getMode() {
        return sMode;
    }

    static synchronized void ensureRegistered(Context context) {
        if (sRegistered) {
            return;
        }
        sRegistered = true;
        // The launcher runs as the foreground user, SystemUI as user 0.
        context.registerReceiverAsUser(new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                String mode = intent.getStringExtra(EXTRA_MODE);
                if (mode != null) {
                    sMode = mode;
                    Log.i(TAG, "Navigation UI mode: " + mode);
                }
            }
        }, UserHandle.ALL, new IntentFilter(ACTION_MODE_CHANGED), null, null,
                Context.RECEIVER_EXPORTED);
    }
}
