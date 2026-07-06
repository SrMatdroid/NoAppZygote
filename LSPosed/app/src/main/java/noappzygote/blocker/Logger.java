package noappzygote.blocker;

import android.util.Log;

import de.robv.android.xposed.XposedBridge;

public class Logger {
    private static final String TAG = "NoAppZygote";

    public static void i(String msg) {
        Log.i(TAG, msg);
        bridge("[" + TAG + "] " + msg);
    }

    public static void e(String msg, Throwable t) {
        Log.e(TAG, msg, t);
        bridge("[" + TAG + "][E] " + msg + " : " + t);
    }

    private static void bridge(String line) {
        try { XposedBridge.log(line); } catch (Throwable ignored) {}
    }
}
