package com.ishland.c2me.opts.dfc.common.ir.node.hir.volume;

import com.ishland.c2me.opts.dfc.common.ir.node.hir.MarkerHIRNode;

/// A symbolic sampling domain. A base volume consumes its group; each
/// transformation consumes another volume, eventually reaching that group.
/// This metadata is distinct from a call site's runtime PackedVolumeNode.
public sealed interface VolumeNode extends MarkerHIRNode
        permits DensityVolumeNode, SITransformationNode, FTPTransformationNode {
}
