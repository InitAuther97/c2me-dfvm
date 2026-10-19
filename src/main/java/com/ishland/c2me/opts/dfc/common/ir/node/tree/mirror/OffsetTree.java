package com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror;

import com.ishland.c2me.opts.dfc.common.ir.node.tree.TreeIRNode;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.noise.FractalNoiseParameters;

/// Mirrors `OffsetDensityFunction` and its `Shift`, `ShiftA` and `ShiftB`
/// variants.
public sealed interface OffsetTree extends TreeIRNode
        permits OffsetTree.Shift, OffsetTree.ShiftA, OffsetTree.ShiftB {

    RegistryEntry<FractalNoiseParameters> offsetNoise();

    @Override
    default int getCost() {
        return 128;
    }

    record Shift(RegistryEntry<FractalNoiseParameters> offsetNoise) implements OffsetTree {
    }

    record ShiftA(RegistryEntry<FractalNoiseParameters> offsetNoise) implements OffsetTree {
    }

    record ShiftB(RegistryEntry<FractalNoiseParameters> offsetNoise) implements OffsetTree {
    }
}