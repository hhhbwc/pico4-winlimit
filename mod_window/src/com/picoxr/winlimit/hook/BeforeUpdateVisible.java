package com.picoxr.winlimit.hook;

import de.robv.android.xposed.XC_MethodHook;

import com.picoxr.winlimit.WindowLimit;

public class BeforeUpdateVisible extends XC_MethodHook {
    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        WindowLimit.IN_UPDATE_VISIBLE.set(Boolean.TRUE);
        int budget = WindowLimit.getWindowLimit() - WindowLimit.STOCK_WINDOW_LIMIT;
        if (budget < 0) budget = 0;
        WindowLimit.DESTROY_BUDGET.set(new int[] { budget });
    }
}
