package com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror;

import com.ishland.c2me.opts.dfc.common.ir.node.tree.TreeIRNode;

/// Mirrors `BlendedNoiseSampler` (registered as `old_blended_noise`).
public record BlendedNoiseTree(
        double xzScale,
        double yScale,
        double xzFactor,
        double yFactor,
        double smearScaleMultiplier
) implements TreeIRNode {
}