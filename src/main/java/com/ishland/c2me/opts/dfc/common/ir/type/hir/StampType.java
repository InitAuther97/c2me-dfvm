package com.ishland.c2me.opts.dfc.common.ir.type.hir;

import com.ishland.c2me.opts.dfc.common.ir.stamp.Stamp;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.TypeRegistry;

import java.util.Objects;

public class StampType extends IrType {

    private static final byte TYPE = TypeRegistry.register(StampType.class);

    public static final StampType BOT = new StampType(null);

    private final Stamp stamp;

    public StampType(Stamp stamp) {
        super(TYPE);
        this.stamp = stamp;
    }

    public static StampType create(Stamp stamp) {
        return new StampType(stamp);
    }

    public Stamp getStamp() {
        return stamp;
    }

    @Override
    protected IrType xmeet(IrType another) {
        final var other = (StampType) another;
        final var stampT = this.stamp;
        final var stampO = other.stamp;
        if (stampT == null || stampO == null || !Stamp.isCompatible(stampO, stampT)) {
            return BOT;
        }
        return new StampType(stampO.merge(stampT));
    }

    @Override
    protected IrType dual() {
        throw new UnsupportedOperationException("Cannot dual() for StampType");
    }

    @Override
    public boolean isDetermined() {
        return true;
    }

    @Override
    protected boolean isSubType(IrType another) {
        return Objects.equals(((StampType) another).stamp, this.stamp);
    }

    @Override
    protected int hash() {
        return Objects.hash(stamp);
    }

    @Override
    public IrType top() {
        throw new UnsupportedOperationException("Cannot top() for StampType");
    }
}
