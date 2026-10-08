package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant;

import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.DoubleType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

public record DoubleConstantNode(double constant) implements ConstantNode {

    @Override
    public IrType initialType() {
        return DoubleType.constant(constant);
    }

    @Override
    public boolean isSame(ConstantNode that) {
        return this.constant == ((DoubleConstantNode) that).constant;
    }

    @Override
    public int hash() {
        return hashCode();
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }
}
