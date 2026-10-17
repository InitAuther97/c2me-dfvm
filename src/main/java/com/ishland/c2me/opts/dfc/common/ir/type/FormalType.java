package com.ishland.c2me.opts.dfc.common.ir.type;

public final class FormalType extends IrType {

    public static final byte TYPE_FORMAL = TypeRegistry.register(IrType.class);

    public static final IrType TOP = new FormalType(false);
    public static final IrType BOT = new FormalType(true);

    private final boolean bot;

    private FormalType(boolean bot) {
        super(TYPE_FORMAL);
        this.bot = bot;
        //noinspection ConditionCoveredByFurtherCondition
        if (TOP != null && BOT != null) {
            throw new IllegalStateException("Attempt to instantiate TopType after initialization");
        }
    }

    @Override
    protected IrType xmeet(IrType another) {
        return bot ? BOT : another;
    }

    @Override
    protected IrType dual() {
        return bot ? TOP : BOT;
    }

    @Override
    public boolean isDetermined() {
        return bot;
    }

    @Override
    protected boolean isSubType(IrType another) {
        final var that = (FormalType) another;
        return !bot || that.bot;
    }

    @Override
    protected int hash() {
        return Boolean.hashCode(bot);
    }

    @Override
    public IrType top() {
        return TOP;
    }
}
