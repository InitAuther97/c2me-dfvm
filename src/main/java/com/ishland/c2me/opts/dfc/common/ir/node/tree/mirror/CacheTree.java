package com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror;

import com.ishland.c2me.opts.dfc.common.ir.node.tree.TreeIRNode;

/// Mirrors `CacheDensityFunction`. The value operand is `[input]`.
public enum CacheTree implements TreeIRNode {

    INSTANCE;

    @Override
    public int getCost() {
        return 1;
    }
}