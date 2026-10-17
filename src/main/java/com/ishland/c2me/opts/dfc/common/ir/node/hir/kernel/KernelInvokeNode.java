package com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.FormalType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

/// Invokes the kernel marked as callee by the memory edge from a
/// {@link KernelDescriptorNode}, passed as this node's first memory operand.
/// The work item to sample is described by the {@link PackedVolumeNode}
/// passed as its second memory operand — typically a degenerate volume whose
/// origin is the mapped coordinate. The packed volume is lowering metadata
/// only: the callee's per-work-item expression reads its own arguments and
/// never the packed volume. The invocation exists only at HIR and yields the
/// callee's value at that work item; the lowering batches invocations of the
/// same callee into one volume fill and converts each to a load from the
/// filled array. Its type is deduced from its callee during computation
/// (FormalType.TOP until then).
public enum KernelInvokeNode implements HIRNode {

    INVOKE;

    @Override
    public void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {
        // TODO: wire the PackedVolumeNode metadata into the invocation
        //       lowering: fill the shared array for the sampled volume and
        //       replace each invocation with a load from it
    }

    @Override
    public IrType initialType() {
        return FormalType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final int callee = IrNode.memory(graph, id, 0);
        return graph.getType(callee);
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        return false;
    }

    @Override
    public boolean isEqual(IrNode that) {
        return that instanceof KernelInvokeNode;
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {
    }

    @Override
    public int getCost() {
        return 0;
    }
}