package com.picoxr.winlimit.hook;

import de.robv.android.xposed.XC_MethodHook;

import com.picoxr.winlimit.WindowLimit;

/**
 * Swallows only the first (limit - 3) handleDestroyApp calls made from inside
 * AppStack.updateVisible. Letting the remaining destroys run is what keeps the
 * OpenXR composition-layer count inside what the runtime can render.
 * Manual app close happens outside updateVisible and is never affected.
 */
public class BlockDestroy extends XC_MethodHook {
    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        if (!Boolean.TRUE.equals(WindowLimit.IN_UPDATE_VISIBLE.get())) {
            return;
        }
        int[] budget = WindowLimit.DESTROY_BUDGET.get();
        if (budget != null && budget[0] > 0) {
            budget[0]--;
            param.setResult(null);
        }
    }
}
