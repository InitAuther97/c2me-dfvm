package com.ishland.c2me.opts.dfc.common.ir.node.tree.group;

import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.MarkerTreeNode;

public enum GroupTagTree implements MarkerTreeNode {
    TAG;

    @Override
    public boolean isEqual(IrNode that) {
        return false;
    }
}
