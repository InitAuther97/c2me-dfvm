package com.ishland.c2me.opts.dfc.common.ir.node.tree;

/// Mirrors `InterpolatedDensityFunction`. The value operand is `[input]`.
public record InterpolatedTree(int cellSizeXz, int cellSizeY) implements TreeIRNode {
}