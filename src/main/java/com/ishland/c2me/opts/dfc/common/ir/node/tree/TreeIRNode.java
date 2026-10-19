package com.ishland.c2me.opts.dfc.common.ir.node.tree;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.FloatType;

/// A node mirroring one node of a vanilla {@code DensityFunction} tree: the
/// tree shape is preserved, children are value operand ids and scalar
/// parameters are copied verbatim. Every tree node produces an f32.
///
/// Tree nodes are an intermediate form only: {@link #lowerToHigh} will
/// rewrite them into high tier IR.
public interface TreeIRNode extends IrNode {

    /// Rewrites this node, and its not yet lowered children, into high tier
    /// IR.
    default void lowerToHigh(IrGraph graph, int id, Canonicalizer canonicalizer) {
        // TODO: lower tree nodes into high tier IR
    }

    @Override
    default IrType initialType() {
        return FloatType.TOP;
    }

    @Override
    default IrType compute(IrGraph graph, int id, GraphContext ctx) {
        return null;
    }

    @Override
    default boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        return false;
    }

    @Override
    default boolean isEqual(IrNode that) {
        return equals(that);
    }

    @Override
    default int hash() {
        return hashCode();
    }

    @Override
    default void export(IrGraph graph, int id, GraphBuilder builder) {
    }

    @Override
    default int getCost() {
        return 0;
    }
}