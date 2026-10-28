package com.ishland.c2me.opts.dfc.common.ir.type.util;

import com.ishland.c2me.opts.dfc.common.ir.type.mir.FloatType;

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

    /// Mirrors vanilla Interval.lerp's numeric bounds, with NaI mapped to ALL.
    /// Its zero-product rules are for range inference, not runtime evaluation.
    public static FloatType lerp(FloatType alpha, FloatType start, FloatType end) {
        if (!alpha.isDetermined() || !start.isDetermined() || !end.isDetermined()) return FloatType.TOP;
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            final float first = (i & 1) == 0 ? start.min() : start.max();
            final float second = (i & 2) == 0 ? end.min() : end.max();
            final FloatType bounds = lerpBounds(alpha, first, second);
            // Vanilla's union ignores NaI corners. Only an entirely NaI result
            // widens to ALL, since FloatType has no NaI representation.
            if (bounds != null) {
                min = Math.min(min, bounds.min());
                max = Math.max(max, bounds.max());
            }
        }
        return of(min, max);
    }

    /// Returns null for a corner interval that vanilla represents as NaI.
    private static FloatType lerpBounds(FloatType alpha, float start, float end) {
        final boolean finite = Float.isFinite(start) && Float.isFinite(end);
        if (!finite && start == end) return FloatType.constant(start);
        final float first = finite ? lerpFinite(alpha.min(), start, end) : lerpInfinite(alpha.min(), start, end);
        final float last = finite ? lerpFinite(alpha.max(), start, end) : lerpInfinite(alpha.max(), start, end);
        // Finite endpoints use Interval.containing, which ignores a single NaN;
        // infinite endpoints reject the pair if either alpha endpoint gives NaN.
        if (!finite && (Float.isNaN(first) || Float.isNaN(last))) return null;
        if (Float.isNaN(first)) return Float.isNaN(last) ? null : FloatType.constant(last);
        if (Float.isNaN(last)) return FloatType.constant(first);
        return FloatType.range(Math.min(first, last), Math.max(first, last));
    }

    private static float lerpFinite(float alpha, float start, float end) {
        return start + lerpProduct(alpha, end - start);
    }

    private static float lerpInfinite(float alpha, float start, float end) {
        final float first = lerpProduct(1.0f - alpha, start);
        final float second = lerpProduct(alpha, end);
        if (!Float.isInfinite(first) || !Float.isInfinite(second)) return first + second;
        if (alpha <= 0.0f) return end > start ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        if (alpha >= 1.0f) return end > start ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
        return Float.NaN;
    }

    private static float lerpProduct(float first, float second) {
        return first != 0.0f && second != 0.0f ? first * second : 0.0f;
    }

    private static FloatType of(float min, float max) {
        if (!(min <= max)) return FloatType.ALL;
        return FloatType.range(min, max);
    }

    private static float min4(float a, float b, float c, float d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static float max4(float a, float b, float c, float d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }
}
