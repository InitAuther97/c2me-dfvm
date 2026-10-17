package com.ishland.c2me.opts.dfc.common.ir.graph.value;

import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.type.FormalType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

public record NodeState(IrNode node, IrType type) {

    public static NodeState create(IrNode node) {
        return new NodeState(node, FormalType.TOP);
    }
}
