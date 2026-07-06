package noappzygote.blocker;

import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class BindHook {

    private static final String HOSTING_RECORD = "com.android.server.am.HostingRecord";

    private static final Set<String> ALLOWED = Set.of(
        "com.android.chrome",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.vivaldi.browser",
        "com.sec.android.app.sbrowser",
        "com.sec.android.app.sbrowser.beta",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "org.chromium.chrome",
        "com.google.android.apps.chrome"
    );

    public static void install(ClassLoader cl) {
        try {
            Class<?> processList = XposedHelpers.findClass(
                    "com.android.server.am.ProcessList", cl);
            int n = XposedBridge.hookAllMethods(processList, "startProcessLocked", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.method == null) return;
                    if (((java.lang.reflect.Method) param.method).getReturnType() != Boolean.TYPE) return;

                    Object hr = null;
                    for (Object arg : param.args) {
                        if (arg == null) continue;
                        if (HOSTING_RECORD.equals(arg.getClass().getName())) {
                            hr = arg;
                            break;
                        }
                    }
                    if (hr == null) return;

                    if (!usesAppZygote(hr)) return;

                    String pkg = getRecordName(hr);
                    if (pkg != null && ALLOWED.contains(pkg)) {
                        Logger.i("allowed app_zygote for " + pkg);
                        return;
                    }

                    Logger.i("blocked app_zygote" + (pkg != null ? " for " + pkg : ""));
                    param.setResult(Boolean.TRUE);
                }
            }).size();
            Logger.i("BindHook installed on ProcessList.startProcessLocked count=" + n);
        } catch (Throwable t) {
            Logger.e("BindHook install failed", t);
        }
    }

    private static boolean usesAppZygote(Object hostingRecord) {
        Boolean uses = Reflect.callTyped(hostingRecord, "usesAppZygote", Boolean.class);
        if (uses != null) return uses;
        try {
            return XposedHelpers.getIntField(hostingRecord, "mHostingZygote") == 2;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String getRecordName(Object hostingRecord) {
        return Reflect.callTyped(hostingRecord, "getRecordName", String.class);
    }
}
