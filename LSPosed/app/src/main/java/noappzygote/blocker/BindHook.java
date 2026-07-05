package noappzygote.blocker;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class BindHook {

    private static final String HOSTING_RECORD = "com.android.server.am.HostingRecord";

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

                    Logger.i("blocked app_zygote start");
                    param.setResult(Boolean.TRUE);
                }
            }).size();
            Logger.i("BindHook installed on ProcessList.startProcessLocked count=" + n);
        } catch (Throwable t) {
            Logger.e("BindHook install failed", t);
        }
    }

    private static boolean usesAppZygote(Object hostingRecord) {
        try {
            Object result = XposedHelpers.callMethod(hostingRecord, "usesAppZygote");
            if (result instanceof Boolean) return ((Boolean) result).booleanValue();
        } catch (Throwable ignored) {
        }
        try {
            return XposedHelpers.getIntField(hostingRecord, "mHostingZygote") == 2;
        } catch (Throwable ignored) {
        }
        return false;
    }
}
