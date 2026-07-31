package com.ishland.c2me.opts.dfc.common.ir.node.mir.memory;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.ValueOpNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.FormalType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.MemoryType;

public enum LoadNode implements MemOpNode, ValueOpNode {

    LOAD;

    @Override
    public IrType initialType() {
        return FormalType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final int mem = IrNode.data(graph, id, 0);
        final var type = graph.getType(mem);
        final MemoryType memory = (MemoryType) type;
        return ctx.getDataType(memory.getAlias());
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        final int mem = graph.getMemory().getOperands(id).getInt(0);
        final var node = graph.getNode(mem);
        // Simple Load-after-Store elimination
        // If memory input is not store, this can't be performed
        if (node != StoreNode.STORE) {
            return false;
        }
        // Load from a stored mem returns the value stored
        final int value = graph.getValue().getOperands(mem).getInt(0);
        IrNode.transferDownstream(graph.getValue(), id, value, canonicalizer);
        IrNode.transferDownstream(graph.getMemory(), id, mem, canonicalizer);
        return true;
    }

    @Override
    public int getCost() {
        return 2;
    }

    @Override
    public boolean isPure(IrGraph graph, int id) {
        return false;
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {
    }
}
