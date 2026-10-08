package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.branch;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.ValueOpNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.BooleanType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.DoubleType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.flowsched.util.Assertions;

public record RangeTestNode(double beginInclusive, double endInclusive) implements ValueOpNode {

    public RangeTestNode {
        Assertions.assertTrue(beginInclusive <= endInclusive);
    }

    @Override
    public IrType initialType() {
        return BooleanType.BOT;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final int upstream = IrNode.data(graph, id, 0);
        final var type = (DoubleType) graph.getType(upstream);
        final var typeThis = DoubleType.range(beginInclusive, endInclusive);
        if (type.is(typeThis)) {
            return BooleanType.TRUE;
        } else if (!type.join(typeThis).isDetermined()) {
            return BooleanType.FALSE;
        }
        return BooleanType.BOT;
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        return false;
    }

    @Override
    public boolean isEqual(IrNode nThat) {
        final var that = (RangeTestNode) nThat;
        return this.beginInclusive == that.beginInclusive && this.endInclusive == that.endInclusive;
    }

    @Override
    public int getCost() {
        return 3;
    }

    @Override
    public int hash() {
        return hashCode();
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }
}
