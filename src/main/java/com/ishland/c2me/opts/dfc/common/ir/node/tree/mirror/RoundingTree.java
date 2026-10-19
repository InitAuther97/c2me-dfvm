package com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror;

import com.ishland.c2me.opts.dfc.common.ir.node.tree.TreeIRNode;
import net.minecraft.world.gen.densityfunction.RoundingDensityFunction;

/// Mirrors `RoundingDensityFunction`. Value operands are `[input, multiple]`.
public record RoundingTree(
        RoundingDensityFunction.Mode type
) implements TreeIRNode {
}