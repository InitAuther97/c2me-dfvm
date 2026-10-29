package com.ishland.c2me.opts.dfc.common.ir.node.hir;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.FormalType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

/// Non-executable HIR annotations. Sampling metadata describes context through
/// memory edges; value markers may forward an input value and override compute.
/// The defaults describe zero-cost metadata without executable memory effects.
public interface MarkerHIRNode extends HIRNode {

    @Override
    default void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {
    }

    @Override
    default IrType initialType() {
        return FormalType.TOP;
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
