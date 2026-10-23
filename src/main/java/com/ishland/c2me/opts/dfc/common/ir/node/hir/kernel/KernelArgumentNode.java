package com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.IntegerType;

import java.util.Objects;

/// An i32 argument passed into a kernel, identified by its name. Its only
/// memory operand is its owning {@link KernelNode}: an ownership marker,
/// not a memory read or execution dependency. This also keeps arguments of
/// different kernels distinct during value numbering. Kernels can
/// freely declare their own formats; {@code type} documents the assumed
/// input type callers must adapt to. Properties over arguments should be
/// made structural, e.g. {@code Mul(16, arg)} expresses an offset that is a
/// multiple of 16.
public record KernelArgumentNode(
        String name,
        IrType type
) implements HIRNode {

    public KernelArgumentNode(String name) {
        this(name, IntegerType.TOP);
    }

    @Override
    public void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {
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
        return that instanceof KernelArgumentNode(String n, IrType t) && Objects.equals(name, n) && Objects.equals(type, t);
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
