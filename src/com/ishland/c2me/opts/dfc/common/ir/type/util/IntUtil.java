package com.ishland.c2me.opts.dfc.common.ir.type.util;

import com.ishland.c2me.opts.dfc.common.ir.type.IntegerType;

public class IntUtil {

    public static IntegerType add(IntegerType first, IntegerType second) {
        if (!first.isDetermined() || !second.isDetermined()) return IntegerType.TOP;
        long min = (long) first.min() + second.min();
        long max = (long) first.max() + second.max();
        return of(min, max);
    }

    public static IntegerType sub(IntegerType first, IntegerType second) {
        if (!first.isDetermined() || !second.isDetermined()) return IntegerType.TOP;
        long min = (long) first.min() - second.max();
        long max = (long) first.max() - second.min();
        return of(min, max);
    }

    public static IntegerType mul(IntegerType first, IntegerType second) {
        if (!first.isDetermined() || !second.isDetermined()) return IntegerType.TOP;
        long p1 = (long) first.min() * second.min();
        long p2 = (long) first.min() * second.max();
        long p3 = (long) first.max() * second.min();
        long p4 = (long) first.max() * second.max();
        return of(min4(p1, p2, p3, p4), max4(p1, p2, p3, p4));
    }

    public static IntegerType div(IntegerType first, IntegerType second) {
        if (!first.isDetermined() || !second.isDetermined()) return IntegerType.TOP;
        if (second.min() <= 0 && second.max() >= 0) return IntegerType.BOT; // divisor may be zero
        long q1 = (long) first.min() / second.min();
        long q2 = (long) first.min() / second.max();
        long q3 = (long) first.max() / second.min();
        long q4 = (long) first.max() / second.max();
        return of(min4(q1, q2, q3, q4), max4(q1, q2, q3, q4));
    }

    private static IntegerType of(long min, long max) {
        // int arithmetic wraps on overflow; a wrapped result set is best approximated by BOT
        if (min < Integer.MIN_VALUE || max > Integer.MAX_VALUE) return IntegerType.BOT;
        return IntegerType.range((int) min, (int) max);
    }

    private static long min4(long a, long b, long c, long d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static long max4(long a, long b, long c, long d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }
}