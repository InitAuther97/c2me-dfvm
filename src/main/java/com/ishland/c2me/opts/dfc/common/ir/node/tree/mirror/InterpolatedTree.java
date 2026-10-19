package com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror;

import com.ishland.c2me.opts.dfc.common.ir.node.tree.TreeIRNode;

/// Mirrors `InterpolatedDensityFunction`. The value operand is `[input]`.
public record InterpolatedTree(int cellSizeXz, int cellSizeY) implements TreeIRNode {

    @Override
    public int getCost() {
        return 16;
    }
}