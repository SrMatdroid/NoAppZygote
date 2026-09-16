# Chrome renderer startup failure

Investigated against upstream commit `f0d5ff80433334c1aa2669bae064980db007eaa5`.
Related reports: [issue 1](https://github.com/SrMatdroid/NoAppZygote/issues/1)
and [issue 4](https://github.com/SrMatdroid/NoAppZygote/issues/4).

## Cause

The browser allowlist calls `HostingRecord.getRecordName()`. That method does
not exist in AOSP HostingRecord. The exception is swallowed, the package becomes
null, and even allowlisted Chrome is blocked. The hook returns `true` without
starting its renderer. Chrome waits for the service instead of rendering pages.

The correct identity is `getDefiningPackageName()`, available in AOSP Android 10
through 16. `getName()` is a component description, not a package. The defining
package also identifies the service owner when an external service's caller
has a different package identity.

Sources: [Android 10 HostingRecord](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/services/core/java/com/android/server/am/HostingRecord.java)
and [Android 16 HostingRecord](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/am/HostingRecord.java).

## Read-only phone findings (2026-09-16)

- Samsung SM-S928B, Android 16 / API 36, Chrome 152.0.7977.82.
- Installed module APK exactly matches upstream's latest LSPosed prebuilt:
  SHA-256 `f3378b5989d79e180f47e4cbd85dbf91915e07f925f4e2af03e868d23ccb67df`.
- Logcat and LSPosed logs repeatedly show `blocked app_zygote` without a package.
- Chrome main and privileged processes exist; no Chrome renderer was present
  in the inspected process list. A later service snapshot contained 38 Chrome
  service records with `app=null` and isolated host PID 0.
- The phone's `services.jar` DEX has no `getRecordName`,
  `getDefiningPackageName`, or `usesAppZygote` methods on HostingRecord.
  It retains `mDefiningPackageName` and `mHostingZygote` fields. Samsung's
  optimized framework therefore requires field fallbacks, including for the
  corrected package lookup.
- Module directory contains ReZygisk, Treat Wheel, and LSPosed; no separate
  NoAppZygote Zygisk module was listed.

Only read commands were issued to the phone. No installs, configuration changes,
process restarts, reboots, flashes, or partition writes were performed.

## Patch and validation

`BindHook` now resolves the defining package with the correct getter, falls
back to `mDefiningPackageName`, and logs field lookup failures. Exact allowlist
matching and blocking of other app zygotes remain unchanged.

Run the host regression suite with a JDK and Python 3:

```sh
python3 tests/run_bind_hook_tests.py
```

The production hook passes 12 cases each for AOSP getters and Samsung fields
only (24 total). Cases cover Chrome/Brave/Edge, non-allowlisted owners, exact
matching, null identity, regular/WebView zygotes, reflection fallbacks, and
irrelevant overloads. The original upstream source fails the Chrome case.
Test doubles model the framework and Xposed API; this is not a device test.

`./gradlew :app:assembleDebug :app:lintDebug` succeeds. Lint reports five
SDK/toolchain/manifest warnings, no errors. APK signature verification passes.
The build output is `LSPosed/app/build/outputs/apk/debug/app-debug.apk`.

## Remaining device validation

The patch has not been installed or activated, so restored Chrome loading and
continued detector blocking are not yet verified on the phone. Its debug
signing certificate differs from the installed upstream APK; it cannot be
installed as an ordinary in-place update. Replacement and activation in
system_server need a separately agreed procedure suitable for this phone's
exploit-based root. No such procedure was attempted.

The native Magisk/Zygisk variant still kills all child zygotes and has no
browser allowlist. This patch addresses the LSPosed variant only; installing
both would not make the native variant honor this exception.
