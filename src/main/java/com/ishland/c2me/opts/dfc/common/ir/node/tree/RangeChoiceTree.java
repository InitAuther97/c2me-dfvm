package com.ishland.c2me.opts.dfc.common.ir.node.tree;

/// Mirrors `RangeChoiceDensityFunction`. Value operands are `[input,
/// whenInRange, whenOutOfRange]`.
public record RangeChoiceTree(
        float minInclusive,
        float maxExclusive
) implements TreeIRNode {
}