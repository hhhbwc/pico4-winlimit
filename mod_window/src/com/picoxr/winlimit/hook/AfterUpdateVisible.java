package com.picoxr.winlimit.hook;

import de.robv.android.xposed.XC_MethodHook;

import com.picoxr.winlimit.WindowLimit;

public class AfterUpdateVisible extends XC_MethodHook {
    @Override
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        WindowLimit.IN_UPDATE_VISIBLE.remove();
        WindowLimit.DESTROY_BUDGET.remove();
    }
}
