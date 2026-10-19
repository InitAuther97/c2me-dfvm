package com.ishland.c2me.opts.dfc.common.ir.node.tree;

import net.minecraft.world.gen.densityfunction.RoundingDensityFunction;

/// Mirrors `RoundingDensityFunction`. Value operands are `[input, multiple]`.
public record RoundingTree(
        RoundingDensityFunction.Mode type
) implements TreeIRNode {
}