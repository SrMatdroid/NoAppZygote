#!/usr/bin/env python3
"""Exercise the production hook on a JVM with small Xposed/framework test doubles.

Requires a JDK (JAVA_HOME optional). No Android device or third-party libraries.
These check hook decisions and reflection, not LSPosed's on-device integration.
"""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCES = {
    "de/robv/android/xposed/XC_MethodHook.java": """
package de.robv.android.xposed;
public class XC_MethodHook {
    public static class MethodHookParam {
        public java.lang.reflect.Member method;
        public Object[] args;
        public Object result;
        public boolean intercepted;
        public void setResult(Object value) { result = value; intercepted = true; }
    }
    protected void beforeHookedMethod(MethodHookParam p) {}
    public void invoke(MethodHookParam p) { beforeHookedMethod(p); }
}
""",
    "de/robv/android/xposed/XposedBridge.java": """
package de.robv.android.xposed;
public class XposedBridge {
    public static XC_MethodHook hook;
    public static java.util.Set<Object> hookAllMethods(Class<?> c, String n, XC_MethodHook h) {
        hook = h;
        return java.util.Collections.singleton(h);
    }
}
""",
    "de/robv/android/xposed/XposedHelpers.java": """
package de.robv.android.xposed;
public class XposedHelpers {
    public static Class<?> findClass(String name, ClassLoader cl) throws Exception {
        return Class.forName(name, false, cl);
    }
    public static Object callMethod(Object o, String name) throws Exception {
        return o.getClass().getMethod(name).invoke(o);
    }
    public static Object getObjectField(Object o, String name) throws Exception {
        java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }
    public static int getIntField(Object o, String name) throws Exception {
        return (Integer) getObjectField(o, name);
    }
}
""",
    "noappzygote/blocker/Logger.java": """
package noappzygote.blocker;
public class Logger {
    public static void i(String s) {}
    public static void e(String s, Throwable t) {}
}
""",
    "com/android/server/am/HostingRecord.java": """
package com.android.server.am;
public final class HostingRecord {
    private final String mDefiningPackageName;
    private final int mHostingZygote;
    public boolean failMethods;
    public HostingRecord(String pkg, int zygote) {
        mDefiningPackageName = pkg;
        mHostingZygote = zygote;
    }
    public String getDefiningPackageName() {
        if (failMethods) throw new UnsupportedOperationException();
        return mDefiningPackageName;
    }
    public boolean usesAppZygote() {
        if (failMethods) throw new UnsupportedOperationException();
        return mHostingZygote == 2;
    }
    // Deliberately a component string, and deliberately no getRecordName().
    public String getName() { return "{com.android.chrome/org.chromium.SandboxedProcessService0}"; }
}
""",
    "com/android/server/am/ProcessList.java": """
package com.android.server.am;
public class ProcessList {
    public boolean startProcessLocked() { return false; }
    public Object otherOverload() { return null; }
}
""",
    "HookTest.java": """
import com.android.server.am.HostingRecord;
import com.android.server.am.ProcessList;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;
import noappzygote.blocker.BindHook;
public class HookTest {
    private static int cases;
    private static void check(String label, Object[] args, boolean blocked, String method)
            throws Exception {
        MethodHookParam p = new MethodHookParam();
        p.method = ProcessList.class.getMethod(method);
        p.args = args;
        XposedBridge.hook.invoke(p);
        if (p.intercepted != blocked || (blocked && !Boolean.TRUE.equals(p.result))) {
            throw new AssertionError(label + ": intercepted=" + p.intercepted);
        }
        cases++;
    }
    private static void check(String label, HostingRecord r, boolean blocked) throws Exception {
        check(label, new Object[] {null, "unrelated", r}, blocked, "startProcessLocked");
    }
    public static void main(String[] args) throws Exception {
        BindHook.install(HookTest.class.getClassLoader());
        check("Chrome renderer", new HostingRecord("com.android.chrome", 2), false);
        check("Brave renderer", new HostingRecord("com.brave.browser", 2), false);
        check("Edge renderer", new HostingRecord("com.microsoft.emmx", 2), false);
        check("non-allowlisted owner despite Chrome component name",
                new HostingRecord("example.detector", 2), true);
        check("exact package match", new HostingRecord("com.android.chrome.fake", 2), true);
        check("unknown owner", new HostingRecord(null, 2), true);
        check("regular zygote", new HostingRecord("example.app", 0), false);
        check("WebView zygote", new HostingRecord("example.app", 1), false);
        HostingRecord fallback = new HostingRecord("com.android.chrome", 2);
        fallback.failMethods = true;
        check("ROM field fallback", fallback, false);
        HostingRecord blockedFallback = new HostingRecord("example.detector", 2);
        blockedFallback.failMethods = true;
        check("field fallback preserves blocking", blockedFallback, true);
        check("no HostingRecord", new Object[] {null, "other"}, false, "startProcessLocked");
        check("non-boolean overload", new Object[] {blockedFallback}, false, "otherOverload");
        System.out.println("PASS: " + cases + " hook regression cases");
    }
}
""",
}


def java_tool(name):
    java_home = os.environ.get("JAVA_HOME")
    return str(Path(java_home) / "bin" / name) if java_home else name


with tempfile.TemporaryDirectory(prefix="noappzygote-tests-") as tmp:
    directory = Path(tmp)
    sources = []
    for name, content in SOURCES.items():
        path = directory / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        sources.append(str(path))
    production = ROOT / "LSPosed/app/src/main/java/noappzygote/blocker/BindHook.java"
    for variant in ("AOSP getters", "Samsung fields only"):
        if variant == "Samsung fields only":
            # Samsung Android 16 services.jar retains the fields but inlines
            # away both getters. Renaming them reproduces method-not-found.
            record = directory / "com/android/server/am/HostingRecord.java"
            record.write_text(record.read_text()
                              .replace("getDefiningPackageName()", "removedPackageGetter()")
                              .replace("usesAppZygote()", "removedZygoteGetter()"))
        print(variant, flush=True)
        subprocess.run([java_tool("javac"), "-d", tmp, *sources, str(production)], check=True)
        subprocess.run([java_tool("java"), "-cp", tmp, "HookTest"], check=True)
