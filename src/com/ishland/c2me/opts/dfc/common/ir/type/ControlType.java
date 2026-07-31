package com.ishland.c2me.opts.dfc.common.ir.type;

public class ControlType extends IrType implements ControlLike {

    public static final ControlType DEAD_CONTROL = new ControlType(false);

    public static final ControlType CONTROL = new ControlType(true);

    private static final byte TYPE_CONTROL = TypeRegistry.register(ControlType.class);

    private final boolean alive;

    private ControlType(boolean alive) {
        super(TYPE_CONTROL);
        this.alive = alive;
    }

    public boolean isAlive() {
        return alive;
    }

    @Override
    protected IrType xmeet(IrType another) {
        return alive ? CONTROL : another;
    }

    @Override
    protected IrType dual() {
        return alive ? DEAD_CONTROL : CONTROL;
    }

    @Override
    public boolean isDetermined() {
        return true;
    }

    @Override
    protected boolean isSubType(IrType another) {
        final var that = (ControlType) another;
        return alive || !that.alive;
    }

    @Override
    protected int hash() {
        return Boolean.hashCode(this.alive);
    }

    @Override
    public IrType top() {
        return DEAD_CONTROL;
    }

    @Override
    public ControlType toControl() {
        return this;
    }
}
