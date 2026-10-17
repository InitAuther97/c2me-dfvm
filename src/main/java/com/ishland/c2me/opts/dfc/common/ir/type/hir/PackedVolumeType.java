package com.ishland.c2me.opts.dfc.common.ir.type.hir;

import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.TypeRegistry;

/// The type of a packed volume: the 9 i32 kernel arguments describing the
/// input volume, combined by
/// {@link com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.PackedVolumeNode}.
public class PackedVolumeType extends IrType {

    private static final byte TYPE_PACKED_VOLUME = TypeRegistry.register(PackedVolumeType.class);

    public static final PackedVolumeType PACKED_VOLUME = new PackedVolumeType();

    private PackedVolumeType() {
        super(TYPE_PACKED_VOLUME);
    }

    @Override
    protected IrType xmeet(IrType another) {
        return PACKED_VOLUME;
    }

    @Override
    protected IrType dual() {
        return this;
    }

    @Override
    public boolean isDetermined() {
        return true;
    }

    @Override
    protected boolean isSubType(IrType another) {
        return true;
    }

    @Override
    protected int hash() {
        return 0;
    }

    @Override
    public IrType top() {
        return this;
    }
}