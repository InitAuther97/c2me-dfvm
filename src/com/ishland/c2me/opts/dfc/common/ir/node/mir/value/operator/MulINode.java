package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.ValueOpNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IntegerType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

public enum MulINode implements ValueOpNode, IntAddMulCommonOp {
    MUL_I;

    @Override
    public IrType initialType() {
        return IntegerType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operands = graph.getValue().getOperands(id);
        final var left = (IntegerType) graph.getType(operands.getInt(0));
        final var right = (IntegerType) graph.getType(operands.getInt(1));
        final int lMin = left.min(), lMax = left.max(), rMin = right.min(), rMax = right.max();
        final int ff = evaluate(lMin, rMin), fs = evaluate(lMin, rMax),
                sf = evaluate(lMax, rMin), ss = evaluate(lMax, rMax);
        final int min = Math.min(Math.min(ff, fs), Math.min(sf, ss));
        final int max = Math.max(Math.max(ff, fs), Math.max(sf, ss));
        return IntegerType.range(min, max);
    }

    @Override
    public int getUnitForOp() {
        return 1;
    }

    @Override
    public boolean isSame(IrNode node) {
        return node == MUL_I;
    }

    @Override
    public int evaluate(int left, int right) {
        return left * right;
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        final var edges = graph.getValue();
        final var operands = edges.getOperands(id);
        final int lhs = operands.getInt(0);
        final int rhs = operands.getInt(1);

        return foldConstant(graph, edges, id, lhs, rhs, canonicalizer) ||
                convertToLeftSpine(graph, edges, id, lhs, rhs, canonicalizer) ||
                foldPartialConstant(graph, edges, id, lhs, rhs);
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }

    @Override
    public int getCost() {
        return 4;
    }
}
