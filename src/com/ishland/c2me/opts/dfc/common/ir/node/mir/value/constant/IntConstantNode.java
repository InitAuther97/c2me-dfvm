package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant;

import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.type.IntegerType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

public record IntConstantNode(int constant) implements ConstantNode {

    @Override
    public IrType initialType() {
        return IntegerType.constant(constant);
    }

    @Override
    public boolean isSame(ConstantNode that) {
        return this.constant == ((IntConstantNode) that).constant;
    }

    @Override
    public int hash() {
        return hashCode();
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }
}
