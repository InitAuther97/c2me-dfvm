package com.ishland.c2me.opts.dfc.common.ir.type.mir;

import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.TypeRegistry;
import com.ishland.flowsched.util.Assertions;

public class FloatType extends NumericType {
    public static final FloatType TOP = new FloatType(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
    public static final FloatType ALL = new FloatType(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);

    private static final byte TYPE_DOUBLE = TypeRegistry.register(FloatType.class);

    private final float min, max;

    private FloatType(float min, float max) {
        super(TYPE_DOUBLE);
        this.min = min;
        this.max = max;
    }

    public float min() {
        return min;
    }

    public float max() {
        return max;
    }

    public static FloatType constant(float value) {
        Assertions.assertTrue(!Float.isNaN(value));
        return new FloatType(value, value);
    }

    /// Bounds may be infinite or reversed for lattice duals; NaN is invalid.
    public static FloatType range(float min, float max) {
        Assertions.assertTrue(!Float.isNaN(min) && !Float.isNaN(max));
        return new FloatType(min, max);
    }

    @Override
    protected IrType xmeet(IrType another) {
        float thatMin = ((FloatType) another).min, thatMax = ((FloatType) another).max;
        return range(Math.min(this.min, thatMin), Math.max(this.max, thatMax));
    }

    /// This implementation meets that join(this, another) = dual(meet(dual(this), dual(another)))
    @Override
    protected IrType dual() {
        return range(max, min);
    }

    @Override
    public boolean isDetermined() {
        return min <= max;
    }

    @Override
    protected boolean isSubType(IrType another) {
        final var that = (FloatType) another;
        return this.min <= that.min && that.max <= this.max;
    }

    @Override
    protected int hash() {
        return Float.hashCode(this.min) * 31 + Float.hashCode(this.max);
    }

    @Override
    public IrType top() {
        return TOP;
    }
}
