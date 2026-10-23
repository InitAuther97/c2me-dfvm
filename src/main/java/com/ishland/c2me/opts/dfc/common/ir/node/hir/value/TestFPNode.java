package com.ishland.c2me.opts.dfc.common.ir.node.hir.value;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.BooleanType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.DoubleType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.FloatType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.TypeUtil;
import com.ishland.flowsched.util.Assertions;

/// Tests a floating point value against lower and upper bounds taken from
/// the graph. Value operands are `[value, lowerBound, upperBound]`; the
/// operand types (f32 or f64) are deduced from the graph. The enum constant
/// selects whether each bound is inclusive. The result is
/// {@link BooleanType#TOP} while any operand type is undetermined, and
/// {@link BooleanType#BOT} when the outcome may go either way.
public enum TestFPNode implements HIRNode {

    MIN_INCLUSIVE_MAX_INCLUSIVE(true, true),
    MIN_INCLUSIVE_MAX_EXCLUSIVE(true, false),
    MIN_EXCLUSIVE_MAX_INCLUSIVE(false, true),
    MIN_EXCLUSIVE_MAX_EXCLUSIVE(false, false);

    private final boolean minInclusive;
    private final boolean maxInclusive;

    TestFPNode(boolean minInclusive, boolean maxInclusive) {
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    @Override
    public void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {
        // TODO: lower to floating point comparisons
    }

    @Override
    public IrType initialType() {
        return BooleanType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final IrType value = TypeUtil.getValueType(graph, IrNode.data(graph, id, 0));
        final IrType lower = TypeUtil.getValueType(graph, IrNode.data(graph, id, 1));
        final IrType upper = TypeUtil.getValueType(graph, IrNode.data(graph, id, 2));
        if (!value.isDetermined() || !lower.isDetermined() || !upper.isDetermined()) {
            return BooleanType.TOP;
        }
        if (value instanceof FloatType v && lower instanceof FloatType l && upper instanceof FloatType u) {
            return test(v.min(), v.max(), l.min(), l.max(), u.min(), u.max());
        }
        if (value instanceof DoubleType v && lower instanceof DoubleType l && upper instanceof DoubleType u) {
            return test(v.min(), v.max(), l.min(), l.max(), u.min(), u.max());
        }
        Assertions.assertTrue(false,
                "TestFPNode operands must be all f32 or all f64 of the same kind, got "
                        + value + ", " + lower + ", " + upper);
        return BooleanType.TOP;
    }

    private BooleanType test(double valueMin, double valueMax,
                             double lowerMin, double lowerMax,
                             double upperMin, double upperMax) {
        final boolean holdsLower = minInclusive ? valueMin >= lowerMax : valueMin > lowerMax;
        final boolean holdsUpper = maxInclusive ? valueMax <= upperMin : valueMax < upperMin;
        if (holdsLower && holdsUpper) {
            return BooleanType.TRUE;
        }
        final boolean belowLower = minInclusive ? valueMax < lowerMin : valueMax <= lowerMin;
        final boolean aboveUpper = maxInclusive ? valueMin > upperMax : valueMin >= upperMax;
        if (belowLower || aboveUpper) {
            return BooleanType.FALSE;
        }
        return BooleanType.BOT;
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
        return 3;
    }
}
