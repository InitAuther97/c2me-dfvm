package com.ishland.c2me.opts.dfc.common.ir.node.hir.value;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.FPBinaryOp;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.FormalType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.DoubleType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.FloatType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.TypeUtil;
import com.ishland.flowsched.util.Assertions;

/// General power operation corresponding to PowerDensityFunction.
/// Value operands are [base, exponent], both f32 or both f64. The f32 result
/// is (float) Math.pow(base, exponent), matching vanilla's general sampler;
/// the f64 result is Math.pow(base, exponent). Literal-exponent sampler
/// specializations are a separate lowering decision. Unary SQUARE/CUBE use
/// PowNFPNode instead, preserving multiplication in the operand's precision.
public enum PowerFPNode implements HIRNode, FPBinaryOp {

    POWER;

    @Override
    public void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {
        // TODO: lower to a power call, preserving the f32 result conversion where needed.
    }

    @Override
    public IrType initialType() {
        return FormalType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operands = graph.getValue().getOperands(id);
        Assertions.assertTrue(operands.size() == 2, "PowerFPNode must have exactly two value operands");
        final IrType base = TypeUtil.getValueType(graph, operands.getInt(0));
        final IrType exponent = TypeUtil.getValueType(graph, operands.getInt(1));
        if (base instanceof FloatType b && exponent instanceof FloatType e) {
            if (!b.isDetermined() || !e.isDetermined()) return FloatType.TOP;
            return computeRange(b.min(), b.max(), e.min(), e.max(), true);
        }
        if (base instanceof DoubleType b && exponent instanceof DoubleType e) {
            if (!b.isDetermined() || !e.isDetermined()) return DoubleType.TOP;
            return computeRange(b.min(), b.max(), e.min(), e.max(), false);
        }
        if (!base.isDetermined() || !exponent.isDetermined()) return FormalType.TOP;
        throw new IllegalStateException("PowerFPNode operands must be both f32 or both f64, got "
                + base + ", " + exponent);
    }

    private IrType computeRange(double baseMin, double baseMax, double exponentMin, double exponentMax,
                                boolean isFloat) {
        final IrType all = isFloat ? FloatType.ALL : DoubleType.ALL;
        if (!canBound(baseMin, baseMax, exponentMin, exponentMax)) return all;

        final double p1 = evaluate(baseMin, exponentMin, isFloat);
        final double p2 = evaluate(baseMin, exponentMax, isFloat);
        final double p3 = evaluate(baseMax, exponentMin, isFloat);
        final double p4 = evaluate(baseMax, exponentMax, isFloat);
        double min = Math.min(Math.min(p1, p2), Math.min(p3, p4));
        final double max = Math.max(Math.max(p1, p2), Math.max(p3, p4));
        if (Double.isNaN(min) || Double.isNaN(max)) return all;

        // An even positive integer power has an interior minimum at zero.
        if (exponentMin == exponentMax && exponentMin > 0.0 && exponentMin % 2.0 == 0.0
                && baseMin <= 0.0 && baseMax >= 0.0) {
            min = 0.0;
        }
        return isFloat ? FloatType.range((float) min, (float) max) : DoubleType.range(min, max);
    }

    private static boolean canBound(double baseMin, double baseMax, double exponentMin, double exponentMax) {
        if (baseMin == baseMax && exponentMin == exponentMax) return true;
        if (exponentMin == exponentMax && Double.isFinite(exponentMin)) {
            if (exponentMin == 0.0) return true;
            // Negative powers across zero can have poles of either sign.
            if (exponentMin < 0.0 && baseMin <= 0.0 && baseMax >= 0.0) return false;
            if (baseMin >= 0.0) return true;
            // A nonintegral exponent with a negative base may produce NaN.
            return exponentMin == Math.rint(exponentMin);
        }
        // For positive bases, extrema occur at the four corners. The interior
        // case Math.pow(1, +/-infinity) can still produce NaN.
        return baseMin > 0.0 && !(baseMin <= 1.0 && baseMax >= 1.0
                && (Double.isInfinite(exponentMin) || Double.isInfinite(exponentMax)));
    }

    private double evaluate(double base, double exponent, boolean isFloat) {
        return isFloat ? evaluate32((float) base, (float) exponent) : evaluate64(base, exponent);
    }

    @Override
    public float evaluate32(float base, float exponent) {
        return (float) Math.pow(base, exponent);
    }

    @Override
    public double evaluate64(double base, double exponent) {
        return Math.pow(base, exponent);
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
        return 16;
    }
}
