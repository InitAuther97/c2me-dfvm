package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.ValueOpNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.DoubleType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.FloatType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.IntegerType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.TypeUtil;
import com.ishland.flowsched.util.Assertions;

public enum FPConvertNode implements ValueOpNode, FPCommonOp {
    F2D, D2F, I2F, F2I;

    @Override
    public IrType initialType() {
        return switch (this) {
            case D2F -> DoubleType.TOP;
            case F2D, I2F -> FloatType.TOP;
            case F2I -> IntegerType.TOP;
        };
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operands = graph.getValue().getOperands(id);
        Assertions.assertTrue(operands.size() == 1, "none or multiple operands are available");
        final int operand = operands.getInt(0);
        final var type = TypeUtil.getValueType(graph, operand);
        if (!type.isDetermined()) return initialType();
        return switch (this) {
            case F2D -> {
                final var t = (FloatType) type;
                yield DoubleType.range(t.min(), t.max());
            }
            case D2F -> {
                final var t = (DoubleType) type;
                yield FloatType.range((float) t.min(), (float) t.max());
            }
            case I2F -> {
                final var t = (IntegerType) type;
                yield FloatType.range((float) t.min(), (float) t.max());
            }
            case F2I -> {
                final var t = (FloatType) type;
                yield IntegerType.range((int) t.min(), (int) t.max());
            }
        };
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
