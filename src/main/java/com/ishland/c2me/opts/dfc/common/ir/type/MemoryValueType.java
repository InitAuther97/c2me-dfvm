package com.ishland.c2me.opts.dfc.common.ir.type;

import com.ishland.c2me.opts.dfc.common.ir.type.mir.MemoryType;

import java.util.Objects;

/// Two results selected by edge kind: a memory state and one value.
/// This is not a tuple of values and cannot contain another compound type.
public final class MemoryValueType extends IrType {

    private static final byte TYPE = TypeRegistry.register(MemoryValueType.class);

    public static final MemoryValueType TOP = new MemoryValueType(MemoryType.TOP, FormalType.TOP);
    public static final MemoryValueType BOT = new MemoryValueType(MemoryType.BOT, FormalType.BOT);

    private final MemoryType memoryType;
    private final IrType valueType;

    public MemoryValueType(MemoryType memoryType, IrType valueType) {
        super(TYPE);
        this.memoryType = Objects.requireNonNull(memoryType);
        this.valueType = Objects.requireNonNull(valueType);
        if (valueType instanceof MemoryValueType || valueType instanceof MemoryType) {
            throw new IllegalArgumentException("Expected a single value type");
        }
    }

    public MemoryType getMemoryType() {
        return memoryType;
    }

    public IrType getValueType() {
        return valueType;
    }

    @Override
    protected IrType xmeet(IrType another) {
        final var that = (MemoryValueType) another;
        return new MemoryValueType((MemoryType) memoryType.meet(that.memoryType), valueType.meet(that.valueType));
    }

    @Override
    protected IrType dual() {
        final IrType memory = memoryType;
        return new MemoryValueType((MemoryType) memory.dual(), valueType.dual());
    }

    @Override
    public boolean isDetermined() {
        return memoryType.isDetermined() && valueType.isDetermined();
    }

    @Override
    protected boolean isSubType(IrType another) {
        final var that = (MemoryValueType) another;
        return memoryType.meet(that.memoryType).equals(memoryType)
                && valueType.meet(that.valueType).equals(valueType);
    }

    @Override
    protected int hash() {
        return 31 * memoryType.hashCode() + valueType.hashCode();
    }

    @Override
    public IrType top() {
        return new MemoryValueType(MemoryType.TOP, valueType.top());
    }
}
