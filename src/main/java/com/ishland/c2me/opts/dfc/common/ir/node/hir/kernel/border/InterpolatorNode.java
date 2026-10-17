package com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.border;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.FloatType;

/// Interpolator divides a region into multiple "cell"s aligned to its
/// dimension (cellSizeXz horizontally, cellSizeY vertically) and calculates
/// each cell's values from its corner values by interpolating three times:
/// horizontally along each axis and vertically. We utilize two small buffers
/// storing corner values on each layer.
///
/// Mirrors vanilla InterpolatedDensityFunction. Value operands: the input
/// density function. On cell corners the input passes through bit-exactly;
/// elsewhere the sample is a trilinear interpolation with fractions
/// `inCell / cellSize` (float division), while bulk fills interpolate at
/// integer offsets with `alpha = offset * (1.0f / cellSize)` and an
/// incremental vertical affine step `(v1 - v0) * (1.0f / cellSizeY)`
/// accumulated per row; those forms differ in rounding and must be
/// preserved. A region already aligned with the cell grid is passed through
/// to the input unchanged.
///
/// Range: the same as the input's (range-tracked through type computation).
public record InterpolatorNode(
        int cellSizeXz,
        int cellSizeY
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
        return that instanceof InterpolatorNode(int xz, int y) && cellSizeXz == xz && cellSizeY == y;
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