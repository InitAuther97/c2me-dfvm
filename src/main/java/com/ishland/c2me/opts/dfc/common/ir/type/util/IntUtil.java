package com.ishland.c2me.opts.dfc.common.ir.type.util;

import com.ishland.c2me.opts.dfc.common.ir.type.mir.IntegerType;

public class IntUtil {

    /// Integer division rounding modes. Operations are defined on `long` so
    /// range endpoint evaluation cannot overflow; casting the final value to
    /// int wraps exactly like int arithmetic, including `MIN_VALUE / -1`.
    public enum Rounding {
        TRUNC, FLOOR, CEIL;

        public long quotient(long left, long right) {
            return switch (this) {
                case TRUNC -> left / right;
                case FLOOR -> Math.floorDiv(left, right);
                case CEIL -> Math.ceilDiv(left, right);
            };
        }

        public long remainder(long left, long right) {
            return switch (this) {
                case TRUNC -> left % right;
                case FLOOR -> Math.floorMod(left, right);
                case CEIL -> Math.ceilMod(left, right);
            };
        }
    }

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

    public static IntegerType min(IntegerType first, IntegerType second) {
        if (!first.isDetermined() || !second.isDetermined()) return IntegerType.TOP;
        return IntegerType.range(Math.min(first.min(), second.min()), Math.min(first.max(), second.max()));
    }

    public static IntegerType max(IntegerType first, IntegerType second) {
        if (!first.isDetermined() || !second.isDetermined()) return IntegerType.TOP;
        return IntegerType.range(Math.max(first.min(), second.min()), Math.max(first.max(), second.max()));
    }

    /// For a sign-uniform divisor every rounding mode is monotone in each
    /// operand, so the four corner quotients bound the result exactly.
    public static IntegerType div(IntegerType first, IntegerType second, Rounding rounding) {
        if (!first.isDetermined() || !second.isDetermined()) return IntegerType.TOP;
        if (second.min() <= 0 && second.max() >= 0) return IntegerType.BOT; // divisor may be zero
        long q1 = rounding.quotient(first.min(), second.min());
        long q2 = rounding.quotient(first.min(), second.max());
        long q3 = rounding.quotient(first.max(), second.min());
        long q4 = rounding.quotient(first.max(), second.max());
        return of(min4(q1, q2, q3, q4), max4(q1, q2, q3, q4));
    }

    /// The remainder's magnitude is bounded by the divisor alone, so its
    /// range needs no endpoint evaluation. Its sign depends on the mode:
    /// truncation follows the dividend, floor follows the divisor, and ceil
    /// is opposite to the divisor.
    public static IntegerType mod(IntegerType first, IntegerType second, Rounding rounding) {
        if (!first.isDetermined() || !second.isDetermined()) return IntegerType.TOP;
        if (second.min() <= 0 && second.max() >= 0) return IntegerType.BOT; // divisor may be zero
        final long magnitude = Math.max(Math.abs((long) second.min()), Math.abs((long) second.max())) - 1;
        return switch (rounding) {
            case TRUNC -> of(
                    first.min() < 0 ? -magnitude : 0,
                    first.max() > 0 ? magnitude : 0);
            case FLOOR -> second.min() > 0 ? of(0, magnitude) : of(-magnitude, 0);
            case CEIL -> second.min() > 0 ? of(-magnitude, 0) : of(0, magnitude);
        };
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