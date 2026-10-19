package com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror;

import com.ishland.c2me.opts.dfc.common.ir.node.tree.TreeIRNode;
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

    @Override
    public int getCost() {
        return 4;
    }
}