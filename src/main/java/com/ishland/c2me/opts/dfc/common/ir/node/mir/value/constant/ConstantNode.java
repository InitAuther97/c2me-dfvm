package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.ValueOpNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

public interface ConstantNode extends ValueOpNode {

    @Override
    default IrType compute(IrGraph graph, int id, GraphContext ctx) {
        return null;
    }

    @Override
    default boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        return false;
    }

    @Override
    default boolean isEqual(IrNode that) {
        return isSame((ConstantNode) that);
    }

    boolean isSame(ConstantNode that);

    @Override
    default int getCost() {
        return 0;
    }
}
