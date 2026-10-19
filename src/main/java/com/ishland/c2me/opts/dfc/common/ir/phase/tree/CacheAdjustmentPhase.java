package com.ishland.c2me.opts.dfc.common.ir.phase.tree;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeAccessor;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.CacheTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.FindTopSurfaceTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.InterpolatedTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.SliceTree;
import com.ishland.c2me.opts.dfc.common.ir.phase.TreePhase;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.BitSet;

/// Removes caches whose cached computation is not worth a memory load.
///
/// For each {@link CacheTree} the cost of the computation it would save is
/// estimated from the tree node costs. A cache is eliminated when that cost
/// does not exceed the cost of a memory load (mirroring the cost of a MIR
/// load), since loading the cached value would then be as expensive as
/// recomputing it. For example in `Noise -> Cache -> +1 -> Cache` the
/// `+1` cache is removed while the noise cache is kept.
///
/// Nested caches are load boundaries: the load is paid whether or not the
/// outer cache exists, so the inner computation is not counted. The walk does
/// not cross {@link SliceTree}, {@link InterpolatedTree} and
/// {@link FindTopSurfaceTree} either: they change the sampling region, and
/// the region a cache samples is part of what it caches.
public class CacheAdjustmentPhase implements TreePhase {

    /// Matches the cost of a memory load in the MIR cost model.
    private static final int LOAD_COST = 4;

    private final GraphContext ctx;

    public CacheAdjustmentPhase(GraphContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void run() {
        final IrGraph graph = ctx.getIrGraph();
        // Eliminate one cache at a time: removing a nested cache exposes its
        // input's cost, which the enclosing cache must then account for.
        while (true) {
            int cache = -1;
            for (int id = 0; id < graph.nodes().size(); id++) {
                if (graph.get(id) == null || !(graph.getNode(id) instanceof CacheTree)) {
                    continue;
                }
                final IntList operands = graph.getValue().getOperands(id);
                if (operands.size() != 1) {
                    throw new IllegalStateException(
                            "Cache node " + id + " must have exactly one input, got " + operands);
                }
                if (savedComputationCost(graph, operands.getInt(0)) <= LOAD_COST) {
                    cache = id;
                    break;
                }
            }
            if (cache == -1) {
                return;
            }
            eliminate(graph, cache);
        }
    }

    /// The cost of the computation a cache with input {@code start} would
    /// save, excluding region-changing subtrees and nested cache loads.
    private static int savedComputationCost(IrGraph graph, int start) {
        final EdgeAccessor value = graph.getValue();
        final BitSet visited = new BitSet();
        final IntArrayList stack = new IntArrayList();
        int cost = 0;
        visited.set(start);
        stack.push(start);
        while (!stack.isEmpty()) {
            final int node = stack.popInt();
            final IrNode irNode = graph.getNode(node);
            if (irNode instanceof CacheTree) {
                // The nested load is paid whether this cache exists.
                continue;
            }
            cost += irNode.getCost();
            if (irNode instanceof SliceTree
                    || irNode instanceof InterpolatedTree
                    || irNode instanceof FindTopSurfaceTree) {
                // Region change: the computation below samples another region.
                if (irNode instanceof InterpolatedTree) {
                    cost += 16;
                } else if (irNode instanceof FindTopSurfaceTree) {
                    cost += 64;
                }
                continue;
            }
            final IntList operands = value.getOperands(node);
            for (int i = 0; i < operands.size(); i++) {
                final int operand = operands.getInt(i);
                if (graph.get(operand) == null || visited.get(operand)) {
                    continue;
                }
                visited.set(operand);
                stack.push(operand);
            }
        }
        return cost;
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