package com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror;

import com.ishland.c2me.opts.dfc.common.ir.node.tree.TreeIRNode;
import net.minecraft.util.math.Spline;
import net.minecraft.world.gen.densityfunction.SplineDensityFunction;

/// Mirrors `SplineDensityFunction`. The spline keeps its vanilla shape; the
/// value operands are its location functions in `Spline#visit` order, one
/// edge per visit (the same node may occur more than once).
public record SplineTree(
        Spline<SplineDensityFunction.DensityFunctionWrapper> spline
) implements TreeIRNode {

    @Override
    public int getCost() {
        return 32;
    }
}