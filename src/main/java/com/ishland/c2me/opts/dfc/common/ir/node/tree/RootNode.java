package com.ishland.c2me.opts.dfc.common.ir.node.tree;

/// Root of the tree stage: START points to every RootNode and each RootNode
/// points to END, making them the only control-spine nodes besides the
/// program boundaries.
public enum RootNode implements TreeIRNode {

    INSTANCE;

    @Override
    public int getCost() {
        return 0;
    }
}
