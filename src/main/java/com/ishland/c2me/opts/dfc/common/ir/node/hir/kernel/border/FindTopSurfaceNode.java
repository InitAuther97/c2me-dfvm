package com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.border;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.FloatType;

/// FindTopSurface finds the highest y at which the input density is positive,
/// reporting that y as a float or lowerBound when no probe is positive. The
/// upper bound density is floored onto a cell grid of cellHeight to yield the
/// first probe position; if it is not above lowerBound, lowerBound is
/// reported directly. Otherwise the input density is probed downward in
/// cellHeight steps down to lowerBound inclusive, and the first y with
/// `sample > 0.0F` wins.
///
/// Mirrors vanilla FindTopSurfaceDensityFunction. Value operands: the input
/// density followed by the upper bound density. The compiled form ignores
/// the y coordinate (vanilla wraps it in a Y-slice at 0), so the value
/// depends only on (x, z).
///
/// Range: `[lowerBound, max(lowerBound, upper bound range max)]` once the
/// upper bound's range is known (range-tracked through type computation).
public record FindTopSurfaceNode(
        int lowerBound,
        int cellHeight
) implements HIRNode {

    @Override
    public void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {
    }

    @Override
    public IrType initialType() {
        return FloatType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var upperBound = graph.getType(IrNode.data(graph, id, 1));
        if (upperBound instanceof FloatType range && range.isDetermined() && Float.isFinite(range.max())) {
            return FloatType.range(lowerBound, Math.max(lowerBound, range.max()));
        }
        return null;
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        return false;
    }

    @Override
    public boolean isEqual(IrNode that) {
        return that instanceof FindTopSurfaceNode(int lb, int ch) && lowerBound == lb && cellHeight == ch;
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