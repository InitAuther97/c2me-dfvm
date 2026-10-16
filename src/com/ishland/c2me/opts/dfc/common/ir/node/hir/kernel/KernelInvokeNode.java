package com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.FloatType;

/// Invokes the kernel marked as callee by the memory edge from a
/// {@link KernelDescriptorNode}, passed as this node's first memory operand.
/// The shape of the invoked kernel is determined by the
/// {@link PackedVolumeNode} passed as its second memory operand. The
/// invocation abstracts a shape-changing then computing operation, and exists
/// only at HIR: it lowers to using an array as a buffer, calling the kernel
/// once to prefill required data, and converting the invocation in every lane
/// to reading the array at the specified location then computing.
public enum KernelInvokeNode implements HIRNode {

    INVOKE;

    @Override
    public void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {
    }

    @Override
    public IrType initialType() {
        return FloatType.TOP;
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