package com.ishland.c2me.opts.dfc.common.ir.node.tree;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.noise.FractalNoiseParameters;

/// Mirrors `NoiseDensityFunction`. Value operands are `[shiftX, shiftY,
/// shiftZ]`.
public record NoiseTree(
        RegistryEntry<FractalNoiseParameters> noise,
        double xzScale,
        double yScale
) implements TreeIRNode {
}