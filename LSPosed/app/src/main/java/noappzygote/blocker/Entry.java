package noappzygote.blocker;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Entry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) return;
        Logger.i("handleLoadPackage on android, processName=" + lpparam.processName);
        BindHook.install(lpparam.classLoader);
    }
}
