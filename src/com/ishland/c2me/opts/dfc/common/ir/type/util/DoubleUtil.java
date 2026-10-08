package com.ishland.c2me.opts.dfc.common.ir.type.util;

import com.ishland.c2me.opts.dfc.common.ir.type.mir.DoubleType;

public class DoubleUtil {

    public static DoubleType add(DoubleType first, DoubleType second) {
        if (!first.isDetermined() || !second.isDetermined()) return DoubleType.TOP;
        return of(first.min() + second.min(), first.max() + second.max());
    }

    public static DoubleType sub(DoubleType first, DoubleType second) {
        if (!first.isDetermined() || !second.isDetermined()) return DoubleType.TOP;
        return of(first.min() - second.max(), first.max() - second.min());
    }

    public static DoubleType mul(DoubleType first, DoubleType second) {
        if (!first.isDetermined() || !second.isDetermined()) return DoubleType.TOP;
        double p1 = first.min() * second.min();
        double p2 = first.min() * second.max();
        double p3 = first.max() * second.min();
        double p4 = first.max() * second.max();
        return of(min4(p1, p2, p3, p4), max4(p1, p2, p3, p4));
    }

    public static DoubleType div(DoubleType first, DoubleType second) {
        if (!first.isDetermined() || !second.isDetermined()) return DoubleType.TOP;
        if (second.min() <= 0.0 && second.max() >= 0.0) return DoubleType.ALL; // divisor may be zero
        double q1 = first.min() / second.min();
        double q2 = first.min() / second.max();
        double q3 = first.max() / second.min();
        double q4 = first.max() / second.max();
        return of(min4(q1, q2, q3, q4), max4(q1, q2, q3, q4));
    }

    public static DoubleType sqrt(DoubleType input) {
        if (!input.isDetermined()) return DoubleType.TOP;
        if (input.min() < 0.0) return DoubleType.ALL; // NaN possible
        return of(Math.sqrt(input.min()), Math.sqrt(input.max()));
    }

    public static DoubleType min(DoubleType first, DoubleType second) {
        if (!first.isDetermined() || !second.isDetermined()) return DoubleType.TOP;
        return of(Math.min(first.min(), second.min()), Math.min(first.max(), second.max()));
    }

    public static DoubleType max(DoubleType first, DoubleType second) {
        if (!first.isDetermined() || !second.isDetermined()) return DoubleType.TOP;
        return of(Math.max(first.min(), second.min()), Math.max(first.max(), second.max()));
    }

    private static DoubleType of(double min, double max) {
        if (!(min <= max) || Double.isInfinite(min) || Double.isInfinite(max)) return DoubleType.ALL;
        return DoubleType.range(min, max);
    }

    private static double min4(double a, double b, double c, double d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static double max4(double a, double b, double c, double d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }
}