package com.ishland.c2me.opts.dfc.common.ir.node.tree;

import net.minecraft.world.gen.densityfunction.BinaryOperationDensityFunction;

/// Mirrors `BinaryOperationDensityFunction`. Value operands are `[left,
/// right]`.
public record BinaryOperationTree(
        BinaryOperationDensityFunction.Type type
) implements TreeIRNode {
}