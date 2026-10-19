package com.ishland.c2me.opts.dfc.common.ir.node.tree;

import it.unimi.dsi.fastutil.floats.FloatList;

/// Mirrors `IntervalSelectDensityFunction`. Value operands are `[input,
/// functions...]`, with branches in list order.
public record IntervalSelectTree(
        FloatList thresholds
) implements TreeIRNode {
}