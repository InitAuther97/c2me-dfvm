package com.ishland.c2me.opts.dfc.common.ir.node.tree;

/// Mirrors `BlendedNoiseSampler` (registered as `old_blended_noise`).
public record BlendedNoiseTree(
        double xzScale,
        double yScale,
        double xzFactor,
        double yFactor,
        double smearScaleMultiplier
) implements TreeIRNode {
}