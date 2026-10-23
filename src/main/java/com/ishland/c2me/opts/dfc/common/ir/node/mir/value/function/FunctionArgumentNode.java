package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.function;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.MIRNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.FormalType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

import java.util.Objects;

/// An input argument of a function: a value read at the function entry. The
/// node takes a memory edge from its {@link FunctionNode} to mark which
/// function declares it, which also keeps arguments of different functions
/// distinct in value numbering. The declared {@code type} is an assumption
/// the function body and its callers must agree on.
public record FunctionArgumentNode(
        String name,
        IrType type
) implements MIRNode {

    public FunctionArgumentNode(String name) {
        this(name, FormalType.TOP);
    }

    @Override
    public IrType initialType() {
        return type;
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
        return that instanceof FunctionArgumentNode(String n, IrType t) && Objects.equals(name, n) && Objects.equals(type, t);
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