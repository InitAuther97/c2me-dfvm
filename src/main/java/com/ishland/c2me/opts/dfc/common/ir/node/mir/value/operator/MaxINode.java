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

public enum MaxINode implements ValueOpNode, IntCommonOp {
    MAX_I;

    @Override
    public IrType initialType() {
        return IntegerType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operands = graph.getValue().getOperands(id);
        final var left = (IntegerType) graph.getType(operands.getInt(0));
        final var right = (IntegerType) graph.getType(operands.getInt(1));
        return IntUtil.max(left, right);
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        final var edges = graph.getValue();
        final var operands = edges.getOperands(id);
        final int lhs = operands.getInt(0), rhs = operands.getInt(1);
        final var lType = (IntegerType) graph.getType(lhs);
        final var rType = (IntegerType) graph.getType(rhs);
        if (lType.isConstant() && rType.isConstant()) {
            final int result = Math.max(lType.min(), rType.min());
            final int con = graph.addNode(new IntConstantNode(result));
            IrNode.transferDownstream(edges, id, con, canonicalizer);
            return true;
        }
        return false;
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }

    @Override
    public int evaluate(int left, int right) {
        return Math.max(left, right);
    }

    @Override
    public int getCost() {
        return 1;
    }
}