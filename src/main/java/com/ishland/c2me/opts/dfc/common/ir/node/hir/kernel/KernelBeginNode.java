package com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.stamp.ForeignKernelStamp;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.ControlType;

/// The control flow source of a kernel function: takes a memory edge from its
/// {@link KernelDescriptorNode} as its first memory operand, marking which
/// function it is the entry of. Only body kernels have a begin node: a
/// {@link KernelDescriptorNode} marked with {@link ForeignKernelStamp} metadata
/// has no implementation in the graph and must not be linked to one. The
/// control flow part of the original KernelNode; it will be lowered to
/// {@link com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.FunctionBeginNode} later.
public enum KernelBeginNode implements HIRNode {

    BEGIN;

    @Override
    public void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {
    }

    @Override
    public IrType initialType() {
        return ControlType.CONTROL;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        return null;
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        return false;
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {
    }

    @Override
    public int getCost() {
        return 0;
    }
}