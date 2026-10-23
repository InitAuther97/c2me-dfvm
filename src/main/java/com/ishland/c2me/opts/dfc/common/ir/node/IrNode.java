package com.ishland.c2me.opts.dfc.common.ir.node;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeAccessor;
import com.ishland.c2me.opts.dfc.common.ir.graph.value.GraphUtil;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.TypeUtil;

import java.util.logging.Logger;

/// IrNode: node is value based and immutable
/// It is safe to use the same instance over different nodes
///
public interface IrNode {

    Logger LOGGER = Logger.getLogger("IrNode");

    /**
     * Compute an initial type for a node
     * @return initial type for a node
     */
    IrType initialType();

    // --- Iterative canonicalization ---

    /**
     * Compute the type for this node, return null if the type does not change.
     * If the returned type equals the original type via {@link IrType#equals(Object)},
     * the type is considered the same.
     * You should not trigger any recursive {@link #compute} or {@link #optimize}
     * during the execution of this method.
     *
     * @param graph The graph the node resides in
     * @param id    Node's id
     * @param ctx   Graph context
     * @return computed type, null if no changes
     * @implNote Read stored types through {@link TypeUtil#getValueType(IrGraph, int)} or
     * {@link TypeUtil#getMemoryType(IrGraph, int)}, according to the result consumed. Use
     * {@link IrGraph#getType} only when the complete type is required; never
     * recursively compute operand types.
     */
    IrType compute(IrGraph graph, int id, GraphContext ctx);

    /**
     * Try to peephole at this node and make a better replacement.
     * You should not trigger any recursive {@link #compute} or {@link #optimize}
     * during the execution of this method.
     *
     * @param graph         The graph the node resides in
     * @param id            Node's id
     * @param canonicalizer canonicalizer, if you need to add far nodes for optimization
     * @param ctx           graph context, including alias allocation, etc.
     * @return if canonicalizer should add neighbors to work queue
     * @implNote Read stored types through {@link TypeUtil#getValueType(IrGraph, int)} or
     * {@link TypeUtil#getMemoryType(IrGraph, int)}, according to the result consumed. Use
     * {@link IrGraph#getType} only when the complete type is required; never
     * recursively compute operand types.
     */
    boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx);

    // --- Global value numbering ---

    /**
     * Determine if this node equals to that node, given that they share same inputs
     *
     * @param that another node
     * @return if they are identical
     */
    default boolean isEqual(IrNode that) {
        return false;
    }

    /**
     * Compute a hash code with the additional metadata of this node
     * @return an additional hash code
     */
    default int hash() {
        return 0xDEADBABE;
    }

    // --- Graph builder ---

    /**
     * Export a directed graph, filling necessary information
     * @param graph The graph the node resides in
     * @param id this node's id
     * @param builder a graph builder
     */
    void export(IrGraph graph, int id, GraphBuilder builder);

    // --- Global code motion

    int getCost();

    // --- Node Utils ---

    static int control(IrGraph graph, int id) {
        return graph.getControl().getOperands(id).getInt(0);
    }

    static int data(IrGraph graph, int id, int idx) {
        return graph.getValue().getOperands(id).getInt(idx);
    }

    static int memory(IrGraph graph, int id, int idx) {
        return graph.getMemory().getOperands(id).getInt(idx);
    }

    static void transferDownstream(EdgeAccessor edge, int idThis, int idThat, Canonicalizer canonicalizer) {
        final var usages = edge.getUsages(idThis);
        canonicalizer.addToWorkQueue(idThat);
        while (!usages.isEmpty()) {
            final int last = GraphUtil.getLast(usages);
            canonicalizer.addToWorkQueue(last);
            edge.replaceEdge(last, idThis, idThat);
        }
    }
}
