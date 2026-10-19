package com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror;

import com.ishland.c2me.opts.dfc.common.ir.node.tree.TreeIRNode;
import it.unimi.dsi.fastutil.floats.FloatList;

/// Mirrors `IntervalSelectDensityFunction`. Value operands are `[input,
/// functions...]`, with branches in list order.
public record IntervalSelectTree(
        FloatList thresholds
) implements TreeIRNode {
}