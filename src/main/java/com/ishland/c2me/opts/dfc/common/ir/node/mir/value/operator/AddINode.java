package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeAccessor;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.ValueOpNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant.IntConstantNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.IntegerType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.IntUtil;

public enum AddINode implements ValueOpNode, IntAddMulCommonOp {

    ADD_I;

    @Override
    public IrType initialType() {
        return IntegerType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operands = graph.getValue().getOperands(id);
        final var left = (IntegerType) graph.getType(operands.getInt(0));
        final var right = (IntegerType) graph.getType(operands.getInt(1));
        return IntUtil.add(left, right);
    }

    @Override
    public int getUnitForOp() {
        return 0;
    }

    @Override
    public int evaluate(int left, int right) {
        return left + right;
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        // We try to convert add to a left-spine add
        final var edges = graph.getValue();
        final var operands = edges.getOperands(id);
        final int lhs = operands.getInt(0);
        final int rhs = operands.getInt(1);
        // Fast path: (x + x) becomes (x * 2)
        // Which may benefit further optimization
        if (lhs == rhs) {
            addSameToMul2(graph, edges, id, lhs, rhs, canonicalizer);
            return true;
        }
        return foldConstant(graph, edges, id, lhs, rhs, canonicalizer) ||
                convertToLeftSpine(graph, edges, id, lhs, rhs, canonicalizer) ||
                foldPartialConstant(graph, edges, id, lhs, rhs);
        // TODO: Phi constant folding (hoisting)
    }

    private static void addSameToMul2(IrGraph graph, EdgeAccessor edges, int id, int lhs, int rhs, Canonicalizer canonicalizer) {
        final int mul = graph.addNode(MulINode.MUL_I);
        final int con = graph.addNode(new IntConstantNode(2));
        edges.removeEdge(lhs, id);
        edges.removeEdge(rhs, id);
        edges.appendEdge(lhs, mul);
        edges.appendEdge(con, mul);
        IrNode.transferDownstream(edges, id, mul, canonicalizer);
        canonicalizer.addToWorkQueue(con);
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {
    }

    @Override
    public int getCost() {
        return 1;
    }
}
