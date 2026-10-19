package com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror;

import com.ishland.c2me.opts.dfc.common.ir.node.tree.TreeIRNode;

/// Mirrors `RangeChoiceDensityFunction`. Value operands are `[input,
/// whenInRange, whenOutOfRange]`.
public record RangeChoiceTree(
        float minInclusive,
        float maxExclusive
) implements TreeIRNode {

    @Override
    public int getCost() {
        return 2;
    }
}