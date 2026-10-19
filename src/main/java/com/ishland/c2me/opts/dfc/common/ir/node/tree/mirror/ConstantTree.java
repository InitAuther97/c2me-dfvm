package com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror;

import com.ishland.c2me.opts.dfc.common.ir.node.tree.TreeIRNode;

/// Mirrors `ConstantDensityFunction`.
public record ConstantTree(float value) implements TreeIRNode {

    @Override
    public int getCost() {
        return 0;
    }
}