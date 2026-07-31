package com.ishland.c2me.opts.dfc.common.ir.type;

import com.ishland.flowsched.util.Assertions;

public class DoubleType extends NumericType {
    public static final DoubleType TOP = new DoubleType(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
    public static final DoubleType ALL = new DoubleType(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

    private static final byte TYPE_DOUBLE = TypeRegistry.register(DoubleType.class);

    private final double min, max;

    private DoubleType(double min, double max) {
        super(TYPE_DOUBLE);
        this.min = min;
        this.max = max;
    }

    public static DoubleType constant(double value) {
        Assertions.assertTrue(Double.isFinite(value));
        return new DoubleType(value, value);
    }

    public static DoubleType range(double min, double max) {
        Assertions.assertTrue(Double.isFinite(min) && Double.isFinite(max) && min <= max);
        return new DoubleType(min, max);
    }

    @Override
    protected IrType xmeet(IrType another) {
        double thatMin = ((DoubleType) another).min, thatMax = ((DoubleType) another).max;
        return range(Math.min(this.min, thatMin), Math.max(this.max, thatMax));
    }

    /// This implementation meets that join(this, another) = dual(meet(dual(this), dual(another)))
    @Override
    protected IrType dual() {
        return range(max, min);
    }

    @Override
    public boolean isDetermined() {
        return min < max;
    }

    @Override
    protected boolean isSubType(IrType another) {
        final var that = (DoubleType) another;
        return this.min <= that.min && that.max <= this.max;
    }

    @Override
    protected int hash() {
        return Double.hashCode(this.min) * 31 + Double.hashCode(this.max);
    }

    @Override
    public IrType top() {
        return TOP;
    }
}
