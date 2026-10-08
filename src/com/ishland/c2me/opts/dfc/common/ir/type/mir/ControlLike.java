package com.ishland.c2me.opts.dfc.common.ir.type.mir;

import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

public interface ControlLike {
    ControlType toControl();

    static ControlType getControl(IrType type) {
        return ((ControlLike) type).toControl();
    }
}
