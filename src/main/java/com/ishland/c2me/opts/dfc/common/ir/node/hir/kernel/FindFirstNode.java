package com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.IntegerType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.TypeUtil;

import java.util.Arrays;

/// Finds the first index of a strided scan for which a predicate kernel
/// holds. The callee (a boolean-returning kernel) is marked by the memory
/// edge from its {@link KernelNode}, passed as this node's first
/// memory operand.
///
/// Value operands: `[lowerBound, upperBound, step, captures...]`. The scan
/// direction is determined solely by the sign of `step`: a positive `step`
/// ascends from `lowerBound` toward `upperBound`, a negative `step` descends
/// from `upperBound` toward `lowerBound`. The index advances by `step` while
/// it remains within the bounds. The first index for which the predicate
/// holds is returned, or the scan's end bound (`upperBound` when ascending,
/// `lowerBound` when descending) when no index matches. When `step < 0` and
/// `upperBound <= lowerBound` the scan is empty and `lowerBound` is returned
/// without probing. `step` must be non-zero.
///
/// Each call to the predicate supplies the sources `[index, captures...]`
/// permuted by the constant `argumentPermutation` table into the callee's
/// declared argument slots: `permutation[j]` is the source feeding the
/// callee's argument `j`, so the callee can preserve any argument layout
/// (e.g. `(capture0, index, capture1)`).
public record FindFirstNode(
        int[] argumentPermutation
) implements HIRNode {

    public FindFirstNode {
        argumentPermutation = argumentPermutation.clone();
    }

    @Override
    public void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {
    }

    @Override
    public IrType initialType() {
        return IntegerType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var lowerBound = (IntegerType) TypeUtil.getValueType(graph, IrNode.data(graph, id, 0));
        final var upperBound = (IntegerType) TypeUtil.getValueType(graph, IrNode.data(graph, id, 1));
        if (!lowerBound.isDetermined() || !upperBound.isDetermined()) {
            return null;
        }
        // The result is either a matched index between the bounds or the end
        // bound itself.
        return IntegerType.range(Math.min(lowerBound.min(), upperBound.min()),
                Math.max(lowerBound.max(), upperBound.max()));
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        return false;
    }

    @Override
    public boolean isEqual(IrNode that) {
        return that instanceof FindFirstNode(int[] other) && Arrays.equals(argumentPermutation, other);
    }

    @Override
    public int hash() {
        return Arrays.hashCode(argumentPermutation);
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {
    }

    @Override
    public int getCost() {
        return 0;
    }
}
