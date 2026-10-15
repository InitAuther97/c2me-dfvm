package com.ishland.c2me.opts.dfc.common.ir.node.hir.spline;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant.FloatConstantNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.FloatType;

/// Value operands: the coordinate followed by one {@link SplineEntry} per
/// point in ascending location order. Each entry's single value operand
/// computes that point's y-value at the sample context.
public enum SplineNode implements HIRNode {
    SPLINE,

    /// A spline verified to have only f32 constant values, assigned by
    /// SplineSpecializePhase: every entry value operand is a
    /// {@link FloatConstantNode}, so evaluation requires no masks and can be
    /// done by gathering the constant table. Derivatives and locations are
    /// unconstrained.
    CONSTANT_TABLE,

    /// A shape-verified uniform-spacing derivative-zero table, assigned by
    /// SplineSpecializePhase: every entry value is an f32 constant, every
    /// derivative is zero, the location stride
    /// {@code step = fl(location[1] - location[0])} is bit-uniform, and every
    /// literal location satisfies
    /// {@code fl(location[0] + fl(i * step)) == location[i]} bitwise.
    /// Consumers may derive segment indices arithmetically, drop all
    /// derivative handling, evaluate out-of-range inputs as the nearest
    /// endpoint value, and recompute locations instead of gathering them.
    UNIFORM_TABLE;

    @Override
    public void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {

    }

    @Override
    public IrType initialType() {
        return FloatType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        return null;
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        return false;
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }

    @Override
    public int getCost() {
        return 0;
    }
}
