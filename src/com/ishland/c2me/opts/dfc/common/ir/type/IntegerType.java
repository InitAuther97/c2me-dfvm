package com.ishland.c2me.opts.dfc.common.ir.type;

public class IntegerType extends NumericType {

    private static final byte TYPE_INTEGER = TypeRegistry.register(IntegerType.class);

    public static final IntegerType TOP = range(Integer.MAX_VALUE, Integer.MIN_VALUE);

    public static final IntegerType BOT = range(Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static IntegerType constant(int value) {
        return new IntegerType(value, value);
    }

    public static IntegerType range(int min, int max) {
        return new IntegerType(min, max);
    }

    private final int min, max;

    public IntegerType(int min, int max) {
        super(TYPE_INTEGER);
        this.min = min;
        this.max = max;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    @Override
    protected IrType xmeet(IrType another) {
        final var that = (IntegerType) another;
        return range(Math.min(this.min, that.min), Math.max(this.max, that.max));
    }

    @Override
    protected IrType dual() {
        return range(this.max, this.min);
    }

    @Override
    public boolean isDetermined() {
        return this.min <= this.max;
    }

    @Override
    protected boolean isSubType(IrType another) {
        final var that = (IntegerType) another;
        return this.min <= that.min && this.max >= that.max;
    }

    @Override
    protected int hash() {
        return Integer.hashCode(min) * 31 + Integer.hashCode(max);
    }

    @Override
    public IrType top() {
        return TOP;
    }

    public boolean isConstant() {
        return this.min == this.max;
    }
}
