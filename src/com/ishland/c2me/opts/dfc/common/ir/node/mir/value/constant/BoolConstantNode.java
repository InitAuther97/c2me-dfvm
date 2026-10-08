package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant;

import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.BooleanType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

public enum BoolConstantNode implements ConstantNode {
    TRUE(true), FALSE(false);

    private final boolean value;

    BoolConstantNode(boolean value) {
        this.value = value;
    }

    @Override
    public IrType initialType() {
        return this.value ? BooleanType.TRUE : BooleanType.FALSE;
    }

    @Override
    public boolean isSame(ConstantNode that) {
        return this.value == ((BoolConstantNode) that).value;
    }

    @Override
    public int hash() {
        return Boolean.hashCode(this.value);
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }
}
