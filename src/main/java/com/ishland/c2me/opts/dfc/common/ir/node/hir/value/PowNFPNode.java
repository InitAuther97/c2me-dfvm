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

/// Raises one floating-point value operand to the instance's fixed power.
/// SQUARE and CUBE are the tree-to-HIR targets of the corresponding unary
/// density operators. The operand determines whether the result is f32 or f64.
/// Evaluation uses left-associated multiplication in that precision:
/// SQUARE is x * x, and CUBE is (x * x) * x, without Math.pow conversion.
public enum PowNFPNode implements HIRNode, FPUnaryOp {

    SQUARE(2),
    CUBE(3);

    private final int times;

    PowNFPNode(int times) {
        this.times = times;
    }

    public int getTimes() {
        return times;
    }

    @Override
    public void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {
        // TODO: expand to times - 1 left-associated multiplications in the operand's precision.
    }

    @Override
    public IrType initialType() {
        return FormalType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operands = graph.getValue().getOperands(id);
        Assertions.assertTrue(operands.size() == 1, "PowNFPNode must have exactly one value operand");
        final IrType input = TypeUtil.getValueType(graph, operands.getInt(0));
        if (!input.isDetermined()) {
            return input;
        }
        return switch (input) {
            case FloatType type -> {
                final float first = evaluate32(type.min());
                final float last = evaluate32(type.max());
                final float min = (times & 1) == 0 && type.min() <= 0.0f && type.max() >= 0.0f
                        ? 0.0f : Math.min(first, last);
                final float max = Math.max(first, last);
                yield FloatType.range(min, max);
            }
            case DoubleType type -> {
                final double first = evaluate64(type.min());
                final double last = evaluate64(type.max());
                final double min = (times & 1) == 0 && type.min() <= 0.0 && type.max() >= 0.0
                        ? 0.0 : Math.min(first, last);
                final double max = Math.max(first, last);
                yield DoubleType.range(min, max);
            }
            default -> throw new IllegalStateException("PowNFPNode operand must be f32 or f64, got " + input);
        };
    }

    @Override
    public float evaluate32(float input) {
        float result = input;
        for (int i = 1; i < times; i++) {
            result *= input;
        }
        return result;
    }

    @Override
    public double evaluate64(double input) {
        double result = input;
        for (int i = 1; i < times; i++) {
            result *= input;
        }
        return result;
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
        return times - 1;
    }
}
