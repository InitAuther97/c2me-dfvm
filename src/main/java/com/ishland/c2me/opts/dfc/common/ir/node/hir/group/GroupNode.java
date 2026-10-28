package com.ishland.c2me.opts.dfc.common.ir.node.hir.group;

import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.MarkerHIRNode;

/// Identity boundary for related kernels. Distinct graph positions never
/// merge under GVN, even when they use the same enum instance.
public enum GroupNode implements MarkerHIRNode {
    TAG;

    @Override
    public boolean isEqual(IrNode that) {
        return false;
    }
}
