package com.ishland.c2me.opts.dfc.common.util;

public class MathUtil {
    public static int ceil2Exp(int i) {
        return 1 << (32 - Integer.numberOfLeadingZeros(i - 1));
    }
}
