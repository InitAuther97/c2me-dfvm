package com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror;

import com.ishland.c2me.opts.dfc.common.ir.node.tree.TreeIRNode;
import net.minecraft.util.math.DistanceMetric;
import net.minecraft.util.math.Vec3i;

/// Mirrors `DistanceToPointDensityFunction`.
public record DistanceToPointTree(Vec3i point, DistanceMetric metric) implements TreeIRNode {
}