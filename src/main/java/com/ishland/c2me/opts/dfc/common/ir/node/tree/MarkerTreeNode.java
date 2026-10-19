package com.ishland.c2me.opts.dfc.common.ir.node.tree;

public interface MarkerTreeNode extends TreeIRNode {
    default int getCost() {
        return 0;
    }
}
