package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.graph.value.NodeState;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.ValueOpNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.DoubleType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.FloatType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.DoubleUtil;
import com.ishland.c2me.opts.dfc.common.ir.type.util.FloatUtil;
import com.ishland.c2me.opts.dfc.common.ir.type.util.TypeUtil;

public enum MulFPNode implements ValueOpNode, FPBinaryOp {
    MUL_F, MUL_D;

    @Override
    public IrType initialType() {
        return this == MUL_D ? DoubleType.TOP : FloatType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operands = graph.getValue().getOperands(id);
        final var first = TypeUtil.getValueType(graph, operands.getInt(0));
        final var second = TypeUtil.getValueType(graph, operands.getInt(1));
        if (this == MUL_D) {
            return DoubleUtil.mul((DoubleType) first, (DoubleType) second);
        } else {
            return FloatUtil.mul((FloatType) first, (FloatType) second);
        }
    }

    // Signed NaN is not respected
    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        final var edges = graph.getValue();
        final var operands = edges.getOperands(id);
        final int left = operands.getInt(0);
        final int right = operands.getInt(1);
        final var leftType = TypeUtil.getValueType(graph, left);
        final var rightType = TypeUtil.getValueType(graph, right);
        if (isConstant(leftType, 1.0)) {
            IrNode.transferDownstream(edges, id, right, canonicalizer);
            return true;
        }
        if (isConstant(rightType, 1.0)) {
            IrNode.transferDownstream(edges, id, left, canonicalizer);
            return true;
        }

        final int constant;
        if (isConstant(leftType, -1.0)) {
            constant = left;
        } else if (isConstant(rightType, -1.0)) {
            constant = right;
        } else {
            return false;
        }

        // -1 * a (or a * -1) becomes -a. NaN bit identity is not required.
        // Keep this node's usages and scheduling edges while dropping the factor.
        edges.removeEdge(constant, id);
        graph.replaceNode(id, new NodeState(this == MUL_D ? NegateFPNode.NEGATE_D : NegateFPNode.NEGATE_F,
                graph.getType(id)));
        canonicalizer.addToWorkQueue(id);
        canonicalizer.addToWorkQueue(constant);
        return true;
    }

    private boolean isConstant(IrType type, double constant) {
        return switch (type) {
            case FloatType value -> this == MUL_F && value.min() == constant && value.max() == constant;
            case DoubleType value -> this == MUL_D && value.min() == constant && value.max() == constant;
            default -> false;
        };
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }

    @Override
    public int getCost() {
        return 0;
    }

    @Override
    public float evaluate32(float left, float right) {
        return left * right;
    }

    @Override
    public double evaluate64(double left, double right) {
        return left * right;
    }
}
