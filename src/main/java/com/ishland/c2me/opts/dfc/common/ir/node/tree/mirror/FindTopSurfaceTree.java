package com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror;

import com.ishland.c2me.opts.dfc.common.ir.node.tree.TreeIRNode;

/// Mirrors `FindTopSurfaceDensityFunction`. Value operands are `[density,
/// upperBound]`.
public record FindTopSurfaceTree(
        int lowerBound,
        int cellHeight
) implements TreeIRNode {

    @Override
    public int getCost() {
        return 64;
    }
}