package com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror;

import com.ishland.c2me.opts.dfc.common.ir.node.tree.TreeIRNode;
import net.minecraft.world.gen.densityfunction.UnaryOperationDensityFunction;

/// Mirrors `UnaryOperationDensityFunction`. Value operands are `[input]`.
public record UnaryOperationTree(
        UnaryOperationDensityFunction.Type type
) implements TreeIRNode {

    @Override
    public int getCost() {
        return switch (type) {
            case ABS, SQUARE, NEGATE, SIGN -> 1;
            case CUBE, HALF_NEGATIVE, QUARTER_NEGATIVE -> 2;
            case SQRT -> 8;
            case RECIPROCAL, SQUEEZE, LOG -> 16;
        };
    }
}