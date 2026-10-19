package com.ishland.c2me.opts.dfc.common.ir.node.tree;

/// Mirrors `ClampDensityFunction`. The value operand is `[input]`.
public record ClampTree(float min, float max) implements TreeIRNode {
}