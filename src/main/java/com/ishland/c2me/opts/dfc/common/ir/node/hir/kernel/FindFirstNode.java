package com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.IntegerType;

import java.util.Arrays;

/// Finds the first index of a descending strided scan for which a predicate
/// kernel holds. The callee (a boolean-returning kernel) is marked by the
/// memory edge from its {@link KernelDescriptorNode}, passed as this node's
/// first memory operand.
///
/// Value operands: `[lowerBound, upperBound, step, captures...]`. The index
/// starts at `lowerBound` and moves toward `upperBound` in steps of `step`,
/// both ends inclusive: when `lowerBound > upperBound` it goes from bigger to
/// smaller, otherwise from smaller to bigger. The first index for which the
/// predicate holds is returned, or the end bound `upperBound` when no index
/// matches. Each call to the predicate supplies the sources
/// `[index, captures...]` permuted by the constant `argumentPermutation`
/// table into the callee's declared argument slots: `permutation[j]` is the
/// source feeding the callee's argument `j`, so the callee can preserve any
/// argument layout (e.g. `(capture0, index, capture1)`).
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
        final var lowerBound = (IntegerType) graph.getType(IrNode.data(graph, id, 0));
        final var upperBound = (IntegerType) graph.getType(IrNode.data(graph, id, 1));
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