package noappzygote.blocker;

import android.util.Log;

import de.robv.android.xposed.XposedBridge;

public class Logger {
    private static final String TAG = "NoAppZygote";

    public static void i(String msg) {
        Log.i(TAG, msg);
        try { XposedBridge.log("[" + TAG + "] " + msg); } catch (Throwable t) {
            Log.w(TAG, "XposedBridge.log failed", t);
        }
    }

    public static void e(String msg, Throwable t) {
        Log.e(TAG, msg, t);
        try { XposedBridge.log("[" + TAG + "][E] " + msg + " : " + t); } catch (Throwable t2) {
            Log.w(TAG, "XposedBridge.log failed", t2);
        }
    }
}
