package com.ishland.c2me.opts.dfc.common.ir.node.tree;

/// Root of the tree stage: START points to every RootNode and each RootNode
/// points to END, making them the only control-spine nodes besides the
/// program boundaries.
///
/// An input root's value operands are `[function, group, identifier]`. Roots
/// split off at a volume change consume `[function, group]` only and carry no
/// identifier, so equal split-off subtrees can deduplicate.
public enum RootNode implements TreeIRNode {

    INSTANCE;

    @Override
    public int getCost() {
        return 0;
    }
}
