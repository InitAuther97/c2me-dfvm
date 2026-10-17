package com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.border;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.FloatType;

/// Slice fixes a set of axes of the input density function at coordinates:
/// the result is constant along every fixed axis, evaluated as the input
/// sampled with each fixed axis's coordinate substituted. The fixed axes are
/// given as a bitmask in `axes` ({@link #AXIS_X}, {@link #AXIS_Y} and
/// {@link #AXIS_Z}, mirroring vanilla's axis flags); `x`, `y` and `z` carry
/// the fixed coordinate per axis, and coordinates of non-fixed axes are
/// ignored.
///
/// Mirrors vanilla SliceDensityFunction: a single fixed axis equals its
/// per-axis samplers, a region degenerate on a fixed axis passes the input
/// through unchanged, and fixing X and Z together equals the fused SamplerXz.
/// A mask of 0 is the identity; a mask of 7 makes the whole value constant.
///
/// Value operands: the input density function.
///
/// Range: the same as the input's (range-tracked through type computation).
public record SliceNode(
        byte axes,
        int x,
        int y,
        int z
) implements HIRNode {

    public static final byte AXIS_X = 1;
    public static final byte AXIS_Y = 2;
    public static final byte AXIS_Z = 4;

    @Override
    public void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {
    }

    @Override
    public IrType initialType() {
        return FloatType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var input = graph.getType(IrNode.data(graph, id, 0));
        if (input instanceof FloatType range && range.isDetermined()) {
            return range;
        }
        return null;
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        return false;
    }

    @Override
    public boolean isEqual(IrNode that) {
        return that instanceof SliceNode(byte a, int fx, int fy, int fz)
                && axes == a && x == fx && y == fy && z == fz;
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
        return 0;
    }
}