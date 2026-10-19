package com.ishland.c2me.opts.dfc.common.ir.node.tree;

import net.minecraft.util.math.Direction;
import net.minecraft.world.gen.densityfunction.Tiling;

/// Mirrors `GradientDensityFunction`.
public record GradientTree(
        Direction.Axis axis,
        Tiling tiling,
        int fromCoordinate,
        int toCoordinate,
        float fromValue,
        float toValue
) implements TreeIRNode {
}