package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.cond;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.ValueOpNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.FormalType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.BooleanType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

/// Selects the second value operand when the first (boolean) operand is
/// true, and the third otherwise. The result type is the taken branch's type
/// when the condition is known; an unknown condition yields the union of
/// both branch types.
public enum SelectNode implements ValueOpNode {

    SELECT;

    @Override
    public IrType initialType() {
        return FormalType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final BooleanType condition = (BooleanType) graph.getType(IrNode.data(graph, id, 0));
        final IrType ifTrue = graph.getType(IrNode.data(graph, id, 1));
        final IrType ifFalse = graph.getType(IrNode.data(graph, id, 2));
        if (condition == BooleanType.TRUE) {
            return ifTrue;
        }
        if (condition == BooleanType.FALSE) {
            return ifFalse;
        }
        return ifTrue.meet(ifFalse);
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        final int ifTrue = IrNode.data(graph, id, 1);
        final int ifFalse = IrNode.data(graph, id, 2);
        if (ifTrue == ifFalse) {
            IrNode.transferDownstream(graph.getValue(), id, ifTrue, canonicalizer);
            return true;
        }
        final BooleanType condition = (BooleanType) graph.getType(IrNode.data(graph, id, 0));
        if (condition == BooleanType.TRUE) {
            IrNode.transferDownstream(graph.getValue(), id, ifTrue, canonicalizer);
            return true;
        }
        if (condition == BooleanType.FALSE) {
            IrNode.transferDownstream(graph.getValue(), id, ifFalse, canonicalizer);
            return true;
        }
        return false;
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }

    @Override
    public int getCost() {
        return 1;
    }
}