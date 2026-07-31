package com.ishland.c2me.opts.dfc.common.ir.type;

public interface ControlLike {
    ControlType toControl();

    static ControlType getControl(IrType type) {
        return ((ControlLike) type).toControl();
    }
}
