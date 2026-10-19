package com.ishland.c2me.opts.dfc.common.ir.node.tree;

import net.minecraft.world.gen.densityfunction.UnaryOperationDensityFunction;

/// Mirrors `UnaryOperationDensityFunction`. Value operands are `[input]`.
public record UnaryOperationTree(
        UnaryOperationDensityFunction.Type type
) implements TreeIRNode {
}