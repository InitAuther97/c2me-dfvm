package com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.MIRNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.FloatType;

import java.util.Objects;

/// The descriptor of a function: declares the function's name and return
/// value type, which is also this node's type. A {@link FunctionBeginNode}
/// takes a memory edge from its descriptor to mark the function it is the
/// control flow source of, and a lowered kernel invocation takes a memory
/// edge from it to mark its callee.
public record FunctionNode(
        String name,
        IrType returnType
) implements MIRNode {

    public FunctionNode(String name) {
        this(name, FloatType.TOP);
    }

    @Override
    public IrType initialType() {
        return returnType;
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
    public boolean isEqual(IrNode that) {
        return that instanceof FunctionNode(String n, IrType t) && Objects.equals(name, n) && Objects.equals(returnType, t);
    }

    @Override
    public int hash() {
        return hashCode();
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {
    }

    @Override
    public int getCost() {
        return 0;
    }
}