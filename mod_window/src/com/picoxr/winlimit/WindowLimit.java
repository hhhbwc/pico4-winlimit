package com.picoxr.winlimit;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import com.picoxr.winlimit.hook.AfterUpdateVisible;
import com.picoxr.winlimit.hook.BeforeUpdateVisible;
import com.picoxr.winlimit.hook.BlockDestroy;

/**
 * Raises the PICO 4 2D app window limit above the stock 3.
 *
 * IMPORTANT: this cannot be raised without bound. The OpenXR runtime has a
 * hard composition-layer ceiling; submitting past it makes xrEndFrame return
 * XR_ERROR_LAYER_LIMIT_EXCEEDED, which desyncs the frame loop and aborts
 * com.picoxr.xrshell (XR_ERROR_CALL_ORDER_INVALID). Measured on this device:
 * 5 windows are stable, 6 break it.
 *
 * AppStack.updateVisible(ZZZI)Z destroys everything past index 3 via
 * subList(3, size). We swallow only the first (limit - 3) of those destroy
 * calls and let the rest proceed, so the stack settles at exactly `limit`.
 */
public class WindowLimit implements IXposedHookLoadPackage {

    public static final String TAG = "PicoWinLimit";

    public static final ThreadLocal<Boolean> IN_UPDATE_VISIBLE = new ThreadLocal<>();
    public static final ThreadLocal<int[]> DESTROY_BUDGET = new ThreadLocal<>();

    public static final int DEFAULT_WINDOW_LIMIT = 5;
    public static final int STOCK_WINDOW_LIMIT = 3;
    public static final int MAX_WINDOW_LIMIT = 5;
    public static final String PROP_WINDOW_LIMIT = "persist.pico.window.limit";
    public static final String RESFIX_PANELS_KEY = "pico_systemext_coord_resfix_panels";

    /** Live-tunable: setprop persist.pico.window.limit N (then restart SystemExt). */
    public static int getWindowLimit() {
        int v = DEFAULT_WINDOW_LIMIT;
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method m = sp.getMethod("getInt", String.class, int.class);
            v = (Integer) m.invoke(null, PROP_WINDOW_LIMIT, DEFAULT_WINDOW_LIMIT);
        } catch (Throwable ignored) {
        }
        if (v < STOCK_WINDOW_LIMIT) v = STOCK_WINDOW_LIMIT;
        int panels = getPersistentDockPanels();
        int max = Math.max(STOCK_WINDOW_LIMIT, MAX_WINDOW_LIMIT - panels);
        if (v > max) v = max;
        return v;
    }

    private static int getPersistentDockPanels() {
        try {
            Object context = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            Object resolver = context.getClass().getMethod("getContentResolver").invoke(context);
            Class<?> cr = Class.forName("android.content.ContentResolver");
            int value = ((Integer) Class.forName("android.provider.Settings$Global")
                    .getMethod("getInt", cr, String.class, int.class)
                    .invoke(null, resolver, RESFIX_PANELS_KEY, 0)).intValue();
            return Math.max(0, Math.min(2, value));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int readRequestedWindowLimit() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (Integer) sp.getMethod("getInt", String.class, int.class)
                    .invoke(null, PROP_WINDOW_LIMIT, DEFAULT_WINDOW_LIMIT);
        } catch (Throwable ignored) {
            return DEFAULT_WINDOW_LIMIT;
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (lp.packageName == null) return;
        if (!"com.picovr.systemext".equals(lp.packageName)) return;

        try {
            Class<?> appStack = XposedHelpers.findClass(
                    "com.bytedance.nativeshell.appmanager.AppStack", lp.classLoader);
            XposedHelpers.findAndHookMethod(appStack, "updateVisible",
                    boolean.class, boolean.class, boolean.class, int.class,
                    new BeforeUpdateVisible());
            XposedHelpers.findAndHookMethod(appStack, "updateVisible",
                    boolean.class, boolean.class, boolean.class, int.class,
                    new AfterUpdateVisible());

            Class<?> appContainer = XposedHelpers.findClass(
                    "com.bytedance.nativeshell.appmanager.AppContainer", lp.classLoader);
            Class<?> rootContainer = XposedHelpers.findClass(
                    "com.bytedance.nativeshell.appmanager.RootAppContainer", lp.classLoader);
            XposedHelpers.findAndHookMethod(rootContainer, "handleDestroyApp",
                    appContainer, new BlockDestroy());

            XposedBridge.log(TAG + ": installed (requested=" + readRequestedWindowLimit()
                    + ", effective=" + getWindowLimit() + ", dockPanels="
                    + getPersistentDockPanels() + ")");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook failed");
            XposedBridge.log(t);
        }
    }
}
