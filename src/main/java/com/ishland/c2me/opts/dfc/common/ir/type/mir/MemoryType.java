package com.ishland.c2me.opts.dfc.common.ir.type.mir;

import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.TypeRegistry;

public class MemoryType extends IrType {
    private static final byte TYPE_MEMORY = TypeRegistry.register(MemoryType.class);
    public static final MemoryType TOP = new MemoryType(Integer.MIN_VALUE);
    public static final MemoryType BOT = new MemoryType(-1);

    private final int alias;

    public MemoryType(int alias) {
        super(TYPE_MEMORY);
        this.alias = alias;
    }

    @Override
    protected IrType xmeet(IrType another) {
        final var that = (MemoryType) another;
        if (this.alias == Integer.MIN_VALUE) {
            return that;
        }
        if (that.alias == Integer.MIN_VALUE) {
            return this;
        }
        if (this.alias == -1 || that.alias == -1 || this.alias != that.alias) {
            return BOT;
        }
        return this;
    }

    @Override
    protected IrType dual() {
        return switch (this.alias) {
            case -1 -> TOP;
            case Integer.MIN_VALUE -> BOT;
            default -> this;
        };
    }

    @Override
    public boolean isDetermined() {
        return this.alias != Integer.MIN_VALUE;
    }

    @Override
    protected boolean isSubType(IrType another) {
        final var that = (MemoryType) another;
        return this.alias == Integer.MIN_VALUE || that.alias == -1 || this.alias == that.alias;
    }

    @Override
    protected int hash() {
        return Integer.hashCode(this.alias);
    }

    @Override
    public IrType top() {
        return TOP;
    }

    public int getAlias() {
        return this.alias;
    }
}
