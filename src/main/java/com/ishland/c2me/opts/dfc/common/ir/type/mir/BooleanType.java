package com.ishland.c2me.opts.dfc.common.ir.type.mir;

import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.TypeRegistry;

public class BooleanType extends IrType {

    public static final BooleanType TOP = new BooleanType((byte) 0b00);
    public static final BooleanType TRUE = new BooleanType((byte) 0b01);
    public static final BooleanType FALSE = new BooleanType((byte) 0b10);
    public static final BooleanType BOT = new BooleanType((byte) 0b11);

    private static final BooleanType[] TYPES = new BooleanType[]{TOP, FALSE, TRUE, BOT};

    private static final byte TYPE_BOOLEAN = TypeRegistry.register(BooleanType.class);

    private final byte data;

    private BooleanType(byte data) {
        super(TYPE_BOOLEAN);
        this.data = data;
    }

    public byte getData() {
        return data;
    }

    @Override
    protected IrType xmeet(IrType another) {
        final var that = (BooleanType) another;
        return TYPES[this.data | that.data];
    }

    @Override
    protected IrType dual() {
        return switch (data) {
            case 0b00 -> BOT;
            case 0b11 -> TOP;
            default -> this;
        };
    }

    @Override
    public boolean isDetermined() {
        return this.data != 0;
    }

    @Override
    protected boolean isSubType(IrType another) {
        final var that = (BooleanType) another;
        return this.data == 0 || that.data == 3 || this.data == that.data;
    }

    @Override
    protected int hash() {
        return 17 * Byte.hashCode(this.data);
    }

    @Override
    public IrType top() {
        return TOP;
    }
}
