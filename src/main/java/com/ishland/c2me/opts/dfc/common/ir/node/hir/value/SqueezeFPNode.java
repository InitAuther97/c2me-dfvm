package com.ishland.c2me.opts.dfc.common.ir.node.hir.value;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.FPUnaryOp;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.FormalType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.DoubleType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.FloatType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.TypeUtil;
import com.ishland.flowsched.util.Assertions;

/// Computes x / 2 - ((x * x) * x) / 24 for one f32 or f64 value operand.
/// The input type must fit in [-1, 1], where the polynomial is monotonic.
/// This node does not clamp: lowering the SQUEEZE unary density operator
/// must clamp its input separately unless its type already proves the bound.
public enum SqueezeFPNode implements HIRNode, FPUnaryOp {

    SQUEEZE;

    @Override
    public void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {
        // TODO: expand the polynomial in the operand's precision, without a clamp.
    }

    @Override
    public IrType initialType() {
        return FormalType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operands = graph.getValue().getOperands(id);
        Assertions.assertTrue(operands.size() == 1, "SqueezeFPNode must have exactly one value operand");
        final IrType input = TypeUtil.getValueType(graph, operands.getInt(0));
        if (!input.isDetermined()) return input;
        return switch (input) {
            case FloatType type -> {
                Assertions.assertTrue(type.min() >= -1.0f && type.max() <= 1.0f,
                        "SqueezeFPNode input range must fit in [-1, 1]");
                yield FloatType.range(evaluate32(type.min()), evaluate32(type.max()));
            }
            case DoubleType type -> {
                Assertions.assertTrue(type.min() >= -1.0 && type.max() <= 1.0,
                        "SqueezeFPNode input range must fit in [-1, 1]");
                yield DoubleType.range(evaluate64(type.min()), evaluate64(type.max()));
            }
            default -> throw new IllegalStateException("SqueezeFPNode operand must be f32 or f64, got " + input);
        };
    }

    @Override
    public float evaluate32(float input) {
        return input / 2.0f - ((input * input) * input) / 24.0f;
    }

    @Override
    public double evaluate64(double input) {
        return input / 2.0 - ((input * input) * input) / 24.0;
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        return false;
    }

    @Override
    public boolean isEqual(IrNode that) {
        return that == this;
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {
    }

    @Override
    public int getCost() {
        return 5;
    }
}
