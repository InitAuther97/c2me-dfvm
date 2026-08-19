package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator;

import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;

public interface CommonOp {
    default boolean isSame(IrNode node) {
        return node == this;
    }
}
