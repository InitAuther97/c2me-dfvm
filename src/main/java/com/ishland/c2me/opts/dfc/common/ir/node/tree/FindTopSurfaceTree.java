package com.ishland.c2me.opts.dfc.common.ir.node.tree;

/// Mirrors `FindTopSurfaceDensityFunction`. Value operands are `[density,
/// upperBound]`.
public record FindTopSurfaceTree(
        int lowerBound,
        int cellHeight
) implements TreeIRNode {
}