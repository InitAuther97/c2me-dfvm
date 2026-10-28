package com.ishland.c2me.opts.dfc.common.ir.node.hir.volume;

import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;

/// Volume transform of a FindTopSurface density input. Its sampled y
/// positions depend on the upper bound value, so the volume cannot be
/// combined with any static transformation. This node never merges under
/// GVN, which isolates every FindTopSurface subtree from all others.
public enum FTPTransformationNode implements VolumeNode {

    INSTANCE;

    @Override
    public boolean isEqual(IrNode that) {
        return false;
    }
}