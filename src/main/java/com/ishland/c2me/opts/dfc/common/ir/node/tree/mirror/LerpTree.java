package com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror;

import com.ishland.c2me.opts.dfc.common.ir.node.tree.TreeIRNode;

/// Mirrors `LerpDensityFunction`. Value operands are `[alpha, first, second]`.
public enum LerpTree implements TreeIRNode {

    INSTANCE;

    @Override
    public int getCost() {
        return 3;
    }
}