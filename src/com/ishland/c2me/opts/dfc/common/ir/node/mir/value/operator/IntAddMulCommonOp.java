package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator;

import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeAccessor;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant.IntConstantNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IntegerType;

public interface IntAddMulCommonOp extends IntCommonOp {
    int getUnitForOp();

    default boolean foldConstant(IrGraph graph, EdgeAccessor edges, int id, int lhs, int rhs, Canonicalizer canonicalizer) {
        final var lType = (IntegerType) graph.getType(lhs);
        final var rType = (IntegerType) graph.getType(rhs);
        // Do constant folding
        if (rType.isConstant()) {
            if (lType.isConstant()) {
                // (con1 + con2) folds into con3
                final int result = evaluate(lType.min(), rType.min());
                final int con = graph.addNode(new IntConstantNode(result));
                IrNode.transferDownstream(edges, id, con, canonicalizer);
                return true;
            }
            if (rType.min() == getUnitForOp()) {
                IrNode.transferDownstream(edges, id, lhs, canonicalizer);
                return true;
            }
        } else if (lType.isConstant() && lType.min() == getUnitForOp()) {
            IrNode.transferDownstream(edges, id, rhs, canonicalizer);
            return true;
        }
        return false;
    }

    default boolean convertToLeftSpine(IrGraph graph, EdgeAccessor edges, int id, int lhs, int rhs, Canonicalizer canonicalizer) {
        final IrNode lNode = graph.getNode(lhs), rNode = graph.getNode(rhs);
        // We may see add(add, add) or add(add, non) or add(non, add) or add(non, non)
        if (isSame(rNode)) {
            if (isSame(lNode)) {
                // ((a + b) + (c + d)) becomes (((a + b) + c) + d)
                final var inputs = edges.getOperands(rhs);
                final int in = inputs.getInt(2);
                edges.removeEdge(in, rhs);
                edges.removeEdge(rhs, id);
                IrNode.transferDownstream(edges, id, rhs, canonicalizer);
                edges.addEdgePair(id, 1, rhs, 2);
                edges.appendEdge(in, id);
                canonicalizer.addToWorkQueue(rhs);
            } else {
                // add(non, add)
                // swap inputs
                edges.removeEdge(lhs, id);
                edges.addEdgePair(lhs, 1, id, 2);
            }
            return true;
        }
        return false;
    }

    // TODO: make it more local by not modifying upstream node's topology structure
    default boolean foldPartialConstant(IrGraph graph, EdgeAccessor edges, int id, int lhs, int rhs) {
        final var rType = (IntegerType) graph.getType(rhs);
        final var lNode = graph.getNode(lhs);
        // Now we can only see left spined add
        if (rType.isConstant()) {
            if (isSame(lNode)) {
                final var lNIns = edges.getOperands(lhs);
                final int lRhs = lNIns.getInt(2);
                final int lLhs = lNIns.getInt(1);
                final var lRType = (IntegerType) graph.getType(lRhs);
                if (lRType.isConstant()) {
                    // ((x + con1) + con2) becomes (x + (con1 + con2))
                    // which folds into (x + con3)
                    edges.replaceEdge(id, lhs, lLhs);
                    edges.replaceEdge(id, rhs, lhs);
                    final int res = evaluate(lRType.min(), rType.min());
                    final int newRhs = graph.addNode(new IntConstantNode(res));
                    edges.replaceEdge(id, rhs, newRhs);
                    return true;
                }
            }
        }
        return false;
    }
}
