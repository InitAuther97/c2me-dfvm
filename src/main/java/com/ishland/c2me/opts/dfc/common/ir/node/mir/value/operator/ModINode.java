package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.ValueOpNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant.IntConstantNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.IntegerType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.IntUtil;

/// Integer remainder with the rounding mode chosen per node instance,
/// mirroring {@link DivINode}: truncating remainder follows the dividend's
/// sign, flooring remainder follows the divisor's, and ceiling remainder is
/// opposite to the divisor's. A divisor that may be zero degrades to
/// {@link IntegerType#BOT}.
public enum ModINode implements ValueOpNode, IntCommonOp {
    MOD_TRUNC(IntUtil.Rounding.TRUNC),
    MOD_FLOOR(IntUtil.Rounding.FLOOR),
    MOD_CEIL(IntUtil.Rounding.CEIL);

    private final IntUtil.Rounding rounding;

    ModINode(IntUtil.Rounding rounding) {
        this.rounding = rounding;
    }

    @Override
    public IrType initialType() {
        return IntegerType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operands = graph.getValue().getOperands(id);
        final var left = (IntegerType) graph.getType(operands.getInt(0));
        final var right = (IntegerType) graph.getType(operands.getInt(1));
        return IntUtil.mod(left, right, rounding);
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        final var type = (IntegerType) graph.getType(id);
        if (!type.isConstant()) {
            return false;
        }
        final int constant = graph.addNode(new IntConstantNode(type.min()));
        IrNode.transferDownstream(graph.getValue(), id, constant, canonicalizer);
        return true;
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }

    @Override
    public int evaluate(int left, int right) {
        return (int) rounding.remainder(left, right);
    }

    @Override
    public int getCost() {
        return 16;
    }
}