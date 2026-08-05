package com.ishland.c2me.opts.dfc.common.ir.type.util;

import com.ishland.c2me.opts.dfc.common.ir.type.FloatType;

public class FloatUtil {

    public static FloatType add(FloatType first, FloatType second) {
        if (!first.isDetermined() || !second.isDetermined()) return FloatType.TOP;
        return of(first.min() + second.min(), first.max() + second.max());
    }

    public static FloatType sub(FloatType first, FloatType second) {
        if (!first.isDetermined() || !second.isDetermined()) return FloatType.TOP;
        return of(first.min() - second.max(), first.max() - second.min());
    }

    public static FloatType mul(FloatType first, FloatType second) {
        if (!first.isDetermined() || !second.isDetermined()) return FloatType.TOP;
        float p1 = first.min() * second.min();
        float p2 = first.min() * second.max();
        float p3 = first.max() * second.min();
        float p4 = first.max() * second.max();
        return of(min4(p1, p2, p3, p4), max4(p1, p2, p3, p4));
    }

    public static FloatType div(FloatType first, FloatType second) {
        if (!first.isDetermined() || !second.isDetermined()) return FloatType.TOP;
        if (second.min() <= 0.0f && second.max() >= 0.0f) return FloatType.ALL; // divisor may be zero
        float q1 = first.min() / second.min();
        float q2 = first.min() / second.max();
        float q3 = first.max() / second.min();
        float q4 = first.max() / second.max();
        return of(min4(q1, q2, q3, q4), max4(q1, q2, q3, q4));
    }

    public static FloatType sqrt(FloatType input) {
        if (!input.isDetermined()) return FloatType.TOP;
        if (input.min() < 0.0f) return FloatType.ALL; // NaN possible
        return of((float) Math.sqrt(input.min()), (float) Math.sqrt(input.max()));
    }

    public static FloatType min(FloatType first, FloatType second) {
        if (!first.isDetermined() || !second.isDetermined()) return FloatType.TOP;
        return of(Math.min(first.min(), second.min()), Math.min(first.max(), second.max()));
    }

    public static FloatType max(FloatType first, FloatType second) {
        if (!first.isDetermined() || !second.isDetermined()) return FloatType.TOP;
        return of(Math.max(first.min(), second.min()), Math.max(first.max(), second.max()));
    }

    private static FloatType of(float min, float max) {
        if (!(min <= max) || Float.isInfinite(min) || Float.isInfinite(max)) return FloatType.ALL;
        return FloatType.range(min, max);
    }

    private static float min4(float a, float b, float c, float d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static float max4(float a, float b, float c, float d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }
}