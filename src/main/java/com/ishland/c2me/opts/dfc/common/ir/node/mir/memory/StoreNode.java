package com.ishland.c2me.opts.dfc.common.ir.node.mir.memory;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.FormalType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

public enum StoreNode implements MemOpNode {

    STORE;

    @Override
    public IrType initialType() {
        return FormalType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        return null;
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        final int mem = graph.getMemory().getOperands(id).getInt(0);
        final int value = graph.getValue().getOperands(id).getInt(0);
        final var memoryUsages = graph.getMemory().getUsages(id);
        boolean eliminated = false, canEliminate = true;
        // The optimization of StoreNode is split into two steps
        // First, do Load-after-Store elimination
        // The eliminated Load is dead code eliminated, which triggers a second optimization pass on this node
        // Then, we do Store-after-Store elimination
        for (int idx = 0; idx < memoryUsages.size(); idx++) {
            final int usage = memoryUsages.getInt(idx);
            final var nodeUsage = graph.getNode(usage);
            // Simple Load-after-Store elimination
            // If memory is not used by Load, this can't be performed
            if (nodeUsage != LoadNode.LOAD) {
                // Record if we can't eliminate the store
                canEliminate &= nodeUsage == STORE;
                continue;
            }
            // Load from a stored mem returns the value stored
            IrNode.transferDownstream(graph.getValue(), usage, value, canonicalizer);
            IrNode.transferDownstream(graph.getMemory(), usage, id, canonicalizer);
            eliminated = true;
        }
        // Pause for now if eliminated any Load
        if (eliminated) {
            return true;
        }
        // Disqualify if any non-Store downstream
        if (!canEliminate) {
            // Check if we're a child Store in a Store-after-Store pattern
            // Otherwise, we may lose this one
            if (graph.getNode(mem) == STORE) canonicalizer.addToWorkQueue(mem);
            return false;
        }
        // Now we only have Store usages
        // Simple Store-after-Store elimination
        // Store to a stored mem shadows the previous Store
        // Here we are the shadowed Store to eliminate
        IrNode.transferDownstream(graph.getMemory(), id, mem, canonicalizer);
        return true;
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }

    @Override
    public int getCost() {
        return 4;
    }
}
