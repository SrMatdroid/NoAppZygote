# NoAppZygote

Bloquea globalmente la creación de procesos `app_zygote` en Android para impedir que **Duck Detector** (y cualquier otra app que use este método) ejecute su oráculo SELinux.

## Contexto

Duck Detector es un detector de entornos root/emulación que utiliza un **oráculo SELinux** para verificar el estado del kernel. En su [PR #22](https://github.com/Wh0ale/DuckDetector/pull/22), implementa una técnica que consiste en:

1. Arrancar un proceso `app_zygote` a través de `ProcessList.startProcessLocked()` con `useAppZygote=true`
2. El `app_zygote` ejecuta `ZygotePreload.doPreload()`, que internamente llama a `selinux_check_access()`
3. Duck Detector lee el resultado desde `/sys/fs/selinux/access` para determinar si SELinux está en un estado manipulable

Este proyecto impide que el `app_zygote` llegue a ejecutarse, cortando el oráculo de raíz.

## Referencias

- **Duck Detector PR #22** – Implementación del oráculo app_zygote: [github.com/Wh0ale/DuckDetector/pull/22](https://github.com/Wh0ale/DuckDetector/pull/22)
- **Isolation-Policy** – Módulo LSPosed de referencia que bloquea app_zygote por app: [github.com/avirajb/Isolation-Policy](https://github.com/avirajb/Isolation-Policy)
- **selinux_check_access()** – Función del kernel que evalúa permisos SELinux: [kernel.org/doc](https://www.kernel.org/doc/html/next/admin-guide/LSM/SELinux.html)
- **Zygisk API** – Documentación de la API de módulos Zygisk de Magisk: [topjohnwu/zygisk-module-sample](https://github.com/topjohnwu/zygisk-module-sample)
- **LSPosed Framework** – Framework Xposed para Android 8.1–14: [github.com/LSPosed/LSPosed](https://github.com/LSPosed/LSPosed)
- **app_zygote en Android** – Child zygote process para aislamiento de apps: [source.android.com/docs/core/runtime/app-zygote](/docs/core/runtime/app-zygote)

## Estructura del proyecto

```
NoAppZygote/
├── LSPosed/              # Módulo LSPosed (Java, hookea system_server)
│   ├── app/
│   │   └── src/main/java/noappzygote/blocker/
│   │       ├── Entry.java         → Punto de entrada Xposed
│   │       ├── BindHook.java      → Hook a ProcessList.startProcessLocked
│   │       └── Logger.java        → Logging helper
│   └── prebuilt/NoAppZygote-LSPosed.apk
├── MagiskZygisk/         # Módulo Magisk Zygisk (C++, mata app_zygote post-fork)
│   ├── source/
│   │   ├── noappzygote.cpp  → Zygisk ModuleBase implementation
│   │   └── zygisk.hpp       → Zygisk API header
│   └── prebuilt/NoAppZygote-Magisk.zip
├── docs/                 # Documentación adicional
└── README.md
```

## Cómo funciona

### LSPosed (recomendado)

El módulo LSPosed hookea `com.android.server.am.ProcessList.startProcessLocked()` en **system_server**. Cuando detecta que el `HostingRecord` tiene `usesAppZygote() == true`, interrumpe el intento devolviendo `Boolean.TRUE` (simula éxito sin llegar a ejecutar el fork). De esta forma el `app_zygote` nunca llega a crearse.

**Instalación:**
1. Instala `NoAppZygote-LSPosed.apk` como una app normal
2. Actívalo en LSPosed Manager con scope = **System Framework** (`android`)
3. Reinicia

**Build:**
```bash
cd LSPosed
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

### Magisk Zygisk (alternativo)

El módulo Magisk+Zygisk se inyecta en cada proceso hijo que zygote crea. Detecta en `preAppSpecialize()` si `is_child_zygote == true` y marca el proceso. En `postAppSpecialize()` ejecuta `_exit(0)` para matar el `app_zygote` inmediatamente después del fork, antes de que llegue a ejecutar `ZygotePreload.doPreload()`.

> ⚠️ Este approach es menos limpio porque el fork ya ocurrió. Duck Detector podría detectar que el app_zygote muere instantáneamente. El módulo LSPosed es la solución recomendada.

**Instalación:**
1. Flashea `NoAppZygote-Magisk.zip` desde Magisk / KernelSU / APatch
2. Asegúrate de que **Zygisk** esté activado en los ajustes de Magisk
3. Reinicia

**Build:**
```bash
cd MagiskZygisk
# Requiere Android NDK
aarch64-linux-android21-clang++ \
    -fPIC -shared \
    -I source \
    source/noappzygote.cpp \
    -o zygisk/arm64.so \
    -llog
zip -r NoAppZygote-Magisk.zip module.prop zygisk/
```

## Notas

- Probado en Android **13/14** (One UI 6.x). La compatibilidad con otras versiones puede variar.
- El módulo LSPosed es la opción **recomendada** porque previene el fork completamente.
- El módulo Magisk está incluido como experimento — puede no funcionar en todas las configuraciones.
- El build del módulo LSPosed requiere el [SDK de Android](https://developer.android.com/studio) y el API de Xposed (gestionado automáticamente por Gradle).

## Licencia

MIT
