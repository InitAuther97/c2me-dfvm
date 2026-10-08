package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.ValueOpNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.DoubleType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.FloatType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.flowsched.util.Assertions;

public enum FPConvertNode implements ValueOpNode, FPCommonOp {
    F2D, D2F;

    @Override
    public IrType initialType() {
        return this == F2D ? FloatType.TOP : DoubleType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operands = graph.getValue().getOperands(id);
        Assertions.assertTrue(operands.size() == 1, "none or multiple operands are available");
        final int operand = operands.getInt(0);
        final var type = graph.getType(operand);
        if (!type.isDetermined()) return initialType();
        if (this == F2D) {
            final var t = (FloatType) type;
            return DoubleType.range(t.min(), t.max());
        } else {
            final var t = (DoubleType) type;
            return FloatType.range((float) t.min(), (float) t.max());
        }
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        final var operands = graph.getValue().getOperands(id);
        Assertions.assertTrue(operands.size() == 1, "none or multiple operands are available");
        final int operand = operands.getInt(0);
        final var node = graph.getNode(operand);
        if (node == F2D && this == D2F) {
            final var upstreamOperands = graph.getValue().getOperands(operand);
            Assertions.assertTrue(upstreamOperands.size() == 1, "none or multiple operands are available");
            final int upstreamOperand = upstreamOperands.getInt(0);
            IrNode.transferDownstream(graph.getValue(), id, upstreamOperand, canonicalizer);
            return true;
        }
        return false;
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }

    @Override
    public int getCost() {
        return 0;
    }
}
