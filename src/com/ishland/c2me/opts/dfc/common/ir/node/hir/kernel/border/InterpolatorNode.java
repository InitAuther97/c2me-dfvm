package com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.border;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

/// Interpolator divides a region into multiple "cell"s aligned to its dimension
/// and calculate each cell's value from cell corners by interpolating three times
/// on each axis.
/// We utilize two small buffers storing corner values on each layer.
public class InterpolatorNode implements HIRNode {

    @Override
    public void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {
    }

    @Override
    public IrType initialType() {
        return null;
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