package com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror;

import com.ishland.c2me.opts.dfc.common.ir.node.tree.TreeIRNode;
import net.minecraft.world.gen.densityfunction.BinaryOperationDensityFunction;

/// Mirrors `BinaryOperationDensityFunction`. Value operands are `[left,
/// right]`.
public record BinaryOperationTree(
        BinaryOperationDensityFunction.Type type
) implements TreeIRNode {
}