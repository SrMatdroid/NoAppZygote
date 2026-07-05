# NoAppZygote

Globally blocks `app_zygote` process spawning on Android. This breaks the SELinux oracle used by **Duck Detector** (PR #22) to detect root/emulation environments.

## How it works

Duck Detector PR #22 uses an app_zygote-based SELinux oracle:

1. Calls `ProcessList.startProcessLocked()` with `useAppZygote=true`
2. The spawned `app_zygote` runs `ZygotePreload.doPreload()`, which calls `selinux_check_access()`
3. Duck Detector reads the result from `/sys/fs/selinux/access` to determine if SELinux is in a compromised state

NoAppZygote prevents the `app_zygote` from ever running, killing the oracle at its root.

## Project structure

```
NoAppZygote/
├── LSPosed/               # LSPosed module (Java, hooks system_server)
│   ├── app/src/main/java/noappzygote/blocker/
│   │   ├── Entry.java     -> Xposed entrypoint
│   │   ├── BindHook.java  -> Hooks ProcessList.startProcessLocked
│   │   └── Logger.java    -> Logging helper
│   └── prebuilt/NoAppZygote-LSPosed.apk
├── MagiskZygisk/          # Magisk Zygisk module (C++, kills app_zygote post-fork)
│   ├── source/
│   │   ├── noappzygote.cpp  -> Zygisk ModuleBase implementation
│   │   └── zygisk.hpp       -> Zygisk API header
│   └── prebuilt/NoAppZygote-Magisk.zip
└── README.md
```

## LSPosed module (recommended)

Hooks `com.android.server.am.ProcessList.startProcessLocked()` in system_server. When it detects `usesAppZygote() == true` on the HostingRecord, it returns `Boolean.TRUE` immediately, preventing the fork from happening at all.

**Install:**
1. Install `NoAppZygote-LSPosed.apk` as a regular app
2. Enable it in LSPosed Manager with scope = **System Framework** (`android`)
3. Reboot

**Build from source:**
```bash
cd LSPosed
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

## Magisk Zygisk module (experimental)

Gets injected into every forked child process. If `is_child_zygote == true`, it calls `_exit(0)` in `postAppSpecialize()`, killing the app_zygote before `ZygotePreload.doPreload()` runs.

Note: this approach is less clean since the fork already happened. The LSPosed module is preferred.

**Install:**
1. Flash `NoAppZygote-Magisk.zip` in Magisk / KernelSU / APatch
2. Make sure **Zygisk** is enabled in Magisk settings
3. Reboot

**Build from source:**
```bash
cd MagiskZygisk
# Requires Android NDK
aarch64-linux-android21-clang++ \
    -fPIC -shared \
    -I source \
    source/noappzygote.cpp \
    -o zygisk/arm64.so \
    -llog
zip -r NoAppZygote-Magisk.zip module.prop zygisk/
```

## References

- [Duck Detector PR #22](https://github.com/eltavine/Duck-Detector-Refactoring/pull/22) – app_zygote SELinux oracle implementation
- [Isolation-Policy](https://github.com/avirajb/Isolation-Policy) – Reference LSPosed module that blocks app_zygote per-app
- [Zygisk Module Sample](https://github.com/topjohnwu/zygisk-module-sample) – Zygisk API documentation
- [LSPosed Framework](https://github.com/LSPosed/LSPosed) – Xposed framework for Android 8.1–14
- [Android app_zygote docs](https://source.android.com/docs/core/runtime/app-zygote) – Child zygote process for app isolation

## Notes

- Tested on **Android 13/14** (One UI 6.x). Other versions may work but YMMV.
- The LSPosed module is the **recommended** approach since it prevents the fork entirely.
- The Magisk module is included as a proof of concept.
- Building the LSPosed module requires the [Android SDK](https://developer.android.com/studio) (Xposed API is pulled automatically by Gradle).

## License

GNU General Public License v3.0 - see [LICENSE](LICENSE)
