package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.ValueOpNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant.IntConstantNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IntegerType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.IntUtil;

public enum DivINode implements ValueOpNode, IntCommonOp {
    DIV_I;

    @Override
    public IrType initialType() {
        return IntegerType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operands = graph.getValue().getOperands(id);
        final var left = (IntegerType) graph.getType(operands.getInt(0));
        final var right = (IntegerType) graph.getType(operands.getInt(1));
        return IntUtil.div(left, right);
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
        return 0;
    }

    @Override
    public boolean isSame(IrNode node) {
        return false;
    }

    @Override
    public int getCost() {
        return 16;
    }
}
