package com.ishland.c2me.opts.dfc.common.ir.phase.tree;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeAccessor;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.MarkerTreeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.group.GroupTagTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.CacheTree;
import com.ishland.c2me.opts.dfc.common.ir.phase.TreePhase;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.BitSet;

/// Eliminates {@link CacheTree} nodes that are reached from only one root
/// of their group: sharing a cache only pays off when several roots of the
/// group evaluate the same subtree.
///
/// For each group the graph is walked bottom-up from every root through
/// value operands, and each node's number of reaching roots is counted.
/// Caches reached by exactly one root are rewired to their input and
/// removed. Reachability is unchanged by elimination (a cache's input is
/// already reachable from every root that reaches the cache), so a single
/// pass suffices.
public class CacheEliminationPhase implements TreePhase {

    private final GraphContext ctx;

    public CacheEliminationPhase(GraphContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void run() {
        final IrGraph graph = ctx.getIrGraph();
        for (int id = 0; id < graph.nodes().size(); id++) {
            if (graph.get(id) != null && graph.getNode(id) instanceof GroupTagTree) {
                eliminateGroupCaches(graph, id);
            }
        }
    }

    private static void eliminateGroupCaches(IrGraph graph, int group) {
        final EdgeAccessor value = graph.getValue();
        final int[] reachingRoots = new int[graph.nodes().size()];
        final IntList roots = value.getUsages(group);
        for (int i = 0; i < roots.size(); i++) {
            final int function = rootFunction(graph, roots.getInt(i));
            if (function == -1) {
                continue;
            }
            final BitSet reachable = new BitSet();
            traverse(graph, function, reachable);
            for (int id = reachable.nextSetBit(0); id >= 0; id = reachable.nextSetBit(id + 1)) {
                reachingRoots[id]++;
            }
        }
        final IntArrayList toEliminate = new IntArrayList();
        for (int id = 0; id < graph.nodes().size(); id++) {
            if (graph.get(id) != null
                    && graph.getNode(id) instanceof CacheTree
                    && reachingRoots[id] == 1) {
                toEliminate.add(id);
            }
        }
        for (int i = 0; i < toEliminate.size(); i++) {
            eliminate(graph, toEliminate.getInt(i));
        }
    }

    /// The function operand of a root, i.e. its value operand that is not a
    /// marker tag; -1 if the root has none.
    private static int rootFunction(IrGraph graph, int root) {
        final IntList operands = graph.getValue().getOperands(root);
        for (int i = 0; i < operands.size(); i++) {
            final int operand = operands.getInt(i);
            final var state = graph.get(operand);
            if (state != null && !(state.node() instanceof MarkerTreeNode)) {
                return operand;
            }
        }
        return -1;
    }

    /// Marks every node reachable from {@code start} through value operands.
    private static void traverse(IrGraph graph, int start, BitSet reachable) {
        final EdgeAccessor value = graph.getValue();
        final IntArrayList stack = new IntArrayList();
        reachable.set(start);
        stack.push(start);
        while (!stack.isEmpty()) {
            final int node = stack.popInt();
            final IntList operands = value.getOperands(node);
            for (int i = 0; i < operands.size(); i++) {
                final int operand = operands.getInt(i);
                if (graph.get(operand) == null || reachable.get(operand)) {
                    continue;
                }
                reachable.set(operand);
                stack.push(operand);
            }
        }
    }

    /// Rewires all users of {@code cache} to its input and removes it.
    private static void eliminate(IrGraph graph, int cache) {
        final EdgeAccessor value = graph.getValue();
        final IntList operands = value.getOperands(cache);
        if (operands.size() != 1) {
            throw new IllegalStateException(
                    "Cache node " + cache + " must have exactly one input, got " + operands);
        }
        final int input = operands.getInt(0);
        final IntList usages = value.getUsages(cache);
        while (!usages.isEmpty()) {
            final int user = usages.getInt(usages.size() - 1);
            value.replaceEdge(user, cache, input);
        }
        value.removeEdge(input, cache);
        if (!graph.isOrphan(cache)) {
            throw new IllegalStateException("Eliminated cache " + cache + " still has usages in other edge kinds");
        }
        graph.removeNode(cache);
    }
}