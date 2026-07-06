package noappzygote.blocker;

import de.robv.android.xposed.XposedHelpers;

final class Reflect {

    private Reflect() {}

    static <T> T callTyped(Object target, String method, Class<T> type) {
        try {
            Object result = XposedHelpers.callMethod(target, method);
            if (type.isInstance(result)) return type.cast(result);
        } catch (Throwable ignored) {
        }
        return null;
    }
}
