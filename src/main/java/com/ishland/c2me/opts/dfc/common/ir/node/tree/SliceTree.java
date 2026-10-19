package com.ishland.c2me.opts.dfc.common.ir.node.tree;

import net.minecraft.util.math.Direction;

/// Mirrors `SliceDensityFunction`. The value operand is `[input]`.
public record SliceTree(Direction.Axis axis, int coordinate) implements TreeIRNode {
}