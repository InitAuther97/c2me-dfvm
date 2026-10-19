package com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror;

import com.ishland.c2me.opts.dfc.common.ir.node.tree.TreeIRNode;

/// Mirrors `EndOuterIslandsDensityFunction`. It has no value operands.
public enum EndOuterIslandsTree implements TreeIRNode {

    INSTANCE;

    @Override
    public int getCost() {
        return 256;
    }
}