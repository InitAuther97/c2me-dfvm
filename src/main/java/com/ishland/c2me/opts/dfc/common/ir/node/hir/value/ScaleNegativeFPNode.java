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

/// HIR target of the HALF_NEGATIVE and QUARTER_NEGATIVE unary density operators.
/// The negative-branch factor is an arbitrary compile-time constant.
/// Takes one f32 or f64 value operand and preserves its precision. Computes
/// x > 0 ? x : x * negativeFactor, including the multiplication path for zero.
public record ScaleNegativeFPNode(double negativeFactor) implements HIRNode, FPUnaryOp {

    public double getNegativeFactor() {
        return negativeFactor;
    }

    @Override
    public void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {
        // TODO: compare against zero and select the original or scaled value.
    }

    @Override
    public IrType initialType() {
        return FormalType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operands = graph.getValue().getOperands(id);
        Assertions.assertTrue(operands.size() == 1, "ScaleNegativeFPNode must have exactly one value operand");
        final IrType input = TypeUtil.getValueType(graph, operands.getInt(0));
        if (!input.isDetermined()) return input;
        return switch (input) {
            case FloatType type -> computeRange(type.min(), type.max(), true);
            case DoubleType type -> computeRange(type.min(), type.max(), false);
            default -> throw new IllegalStateException("ScaleNegativeFPNode operand must be f32 or f64, got " + input);
        };
    }

    private IrType computeRange(double inputMin, double inputMax, boolean isFloat) {
        final double first = evaluate(inputMin, isFloat);
        final double last = evaluate(inputMax, isFloat);
        double min = Math.min(first, last);
        double max = Math.max(first, last);
        if (inputMin < inputMax && inputMin <= 0.0 && inputMax >= 0.0) {
            // The two branches can have different slopes. Include their boundary
            // and both zero signs, which multiplication may reverse.
            final double negativeZero = evaluate(-0.0, isFloat);
            final double positiveZero = evaluate(0.0, isFloat);
            min = Math.min(min, Math.min(negativeZero, positiveZero));
            max = Math.max(max, Math.max(negativeZero, positiveZero));
        }
        // Numeric range bounds cannot represent NaN (e.g. zero times infinity).
        if (Double.isNaN(min) || Double.isNaN(max)) return isFloat ? FloatType.ALL : DoubleType.ALL;
        return isFloat ? FloatType.range((float) min, (float) max) : DoubleType.range(min, max);
    }

    private double evaluate(double input, boolean isFloat) {
        return isFloat ? evaluate32((float) input) : evaluate64(input);
    }

    @Override
    public float evaluate32(float input) {
        return input > 0.0f ? input : input * (float) negativeFactor;
    }

    @Override
    public double evaluate64(double input) {
        return input > 0.0 ? input : input * negativeFactor;
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        return false;
    }

    @Override
    public boolean isEqual(IrNode that) {
        return equals(that);
    }

    @Override
    public int hash() {
        return hashCode();
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {
    }

    @Override
    public int getCost() {
        return 2;
    }
}
