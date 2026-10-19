package com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror;

import com.ishland.c2me.opts.dfc.common.ir.node.tree.TreeIRNode;

/// Mirrors vanilla `BlendDensityFunction`, whose variants carry no operands.
public enum BlendDensityTree implements TreeIRNode {

    BLEND_ALPHA(4),
    BLEND_OFFSET(4),
    BEARDIFIER(8);

    private final int cost;

    BlendDensityTree(int cost) {
        this.cost = cost;
    }

    @Override
    public int getCost() {
        return cost;
    }
}