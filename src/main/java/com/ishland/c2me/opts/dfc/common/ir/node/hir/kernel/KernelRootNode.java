package com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.type.FormalType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.TypeUtil;

/// Root of a pure HIR value DAG. Memory operand 0 is the owning
/// {@link KernelNode}; value operand 0 is the kernel's result. START points
/// to each root and each root points to END for structural reachability,
/// not executable control flow. Foreign kernels have no body root.
/// Implicit short-circuits in the body become control flow during MIR lowering.
public enum KernelRootNode implements HIRNode {

    ROOT;

    @Override
    public void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {
    }

    @Override
    public IrType initialType() {
        return FormalType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        return TypeUtil.getValueType(graph, IrNode.data(graph, id, 0));
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
