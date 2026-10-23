package com.ishland.c2me.opts.dfc.common.ir.phase.scalar;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.LongUtil;
import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeAccessor;
import com.ishland.c2me.opts.dfc.common.ir.graph.value.GraphUtil;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.phase.HighTierPhase;
import com.ishland.c2me.opts.dfc.common.ir.phase.MidTierPhase;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.BitSet;
import java.util.Objects;

public class GVN extends RunCanonicalizerPhase implements IntHash.Strategy, HighTierPhase, MidTierPhase {

    private final IrGraph graph;
    private final Int2IntMap globalMap = new Int2IntOpenCustomHashMap(this);

    public GVN(GraphContext ctx) {
        super(ctx);
        this.graph = ctx.getIrGraph();
        globalMap.defaultReturnValue(-1);
    }

    /**
     * Run Global Value Numbering (GVN) on this graph. <br/>
     * Value and memory edges together form the dependency graph. Nodes are
     * iterated in reverse post order over both edge kinds, starting from
     * nodes without operands, so every definition is numbered before its
     * users. For every node met, try to find a replacement in the map; if a
     * replacement exists, replace all value and memory usages of this node
     * with it. Programs are not required: nodes whose {@code isEqual} is
     * false (e.g. CFG nodes) simply never find a replacement. <br/>
     */
    @Override
    public void run() {
        final IntArrayList postOrder = new IntArrayList();
        final BitSet visited = new BitSet();
        final EdgeAccessor value = graph.getValue();
        final EdgeAccessor memory = graph.getMemory();
        // Definitions first: start from nodes without value or memory
        // operands and collect their usages.
        for (int id = 0; id < graph.nodes().size(); id++) {
            if (graph.get(id) != null
                    && value.getOperands(id).isEmpty()
                    && memory.getOperands(id).isEmpty()) {
                collectPostOrder(id, visited, postOrder);
            }
        }
        // Covers components without a source, e.g. value cycles through phis.
        for (int id = 0; id < graph.nodes().size(); id++) {
            if (graph.get(id) != null && !visited.get(id)) {
                collectPostOrder(id, visited, postOrder);
            }
        }
        // Reverse post order of a def->use walk visits definitions first.
        for (int i = postOrder.size() - 1; i >= 0; i--) {
            final int node = postOrder.getInt(i);
            if (graph.get(node) == null) continue;
            if (!number(node)) continue;
            // Add to canonicalizer for dead code elimination
            enqueue(node);
        }
    }

    /// Collects a post-order over value and memory usages from {@code start}.
    private void collectPostOrder(int start, BitSet visited, IntArrayList postOrder) {
        final EdgeAccessor value = graph.getValue();
        final EdgeAccessor memory = graph.getMemory();
        final LongArrayList stack = new LongArrayList();
        stack.push(LongUtil.make(start, 0)); // 0 = enter, 1 = leave
        while (!stack.isEmpty()) {
            final long entry = stack.popLong();
            final int node = LongUtil.getLow(entry);
            final int phase = LongUtil.getHigh(entry);
            if (phase == 0) {
                if (visited.get(node)) continue;
                visited.set(node);
                stack.push(LongUtil.make(node, 1));
                final IntList vUsages = value.getUsages(node);
                for (int i = vUsages.size() - 1; i >= 0; i--) {
                    final int usage = vUsages.getInt(i);
                    if (!visited.get(usage)) stack.push(LongUtil.make(usage, 0));
                }
                final IntList mUsages = memory.getUsages(node);
                for (int i = mUsages.size() - 1; i >= 0; i--) {
                    final int usage = mUsages.getInt(i);
                    if (!visited.get(usage)) stack.push(LongUtil.make(usage, 0));
                }
            } else {
                postOrder.add(node);
            }
        }
    }

    boolean number(int node) {
        final var control = graph.getControl();
        final var value = graph.getValue();
        final var memory = graph.getMemory();
        // Compute for a replacement
        final int replacement = globalMap.putIfAbsent(node, node);
        if (replacement == -1) {
            // We are not replaced
            return false;
        }
        // We're technically done for replacement calculation now
        final var scheduling = control.getOperands(node);
        final var vUses = value.getUsages(node);
        final var mUses = memory.getUsages(node);
        // Do actual replacement
        while (!vUses.isEmpty()) {
            final int downstream = GraphUtil.getLast(vUses);
            value.replaceEdge(downstream, node, replacement);
        }

        while (!mUses.isEmpty()) {
            final int downstream = GraphUtil.getLast(mUses);
            memory.replaceEdge(downstream, node, replacement);
        }

        // Carry over scheduling edges without duplicating those the
        // replacement already has (e.g. two roots under the same entry).
        final var replacementScheduling = control.getOperands(replacement);
        for (int j = 0; j < scheduling.size(); j++) {
            final int controlOperand = scheduling.getInt(j);
            if (!replacementScheduling.contains(controlOperand)) {
                control.appendEdge(controlOperand, replacement);
            }
        }
        return true;
    }

    /// Hash strategy for node id
    @Override
    public int hashCode(int e) {
        final var vOps = graph.getValue().getOperands(e);
        final var mOps = graph.getMemory().getOperands(e);
        final var node = graph.getNode(e);
        return Objects.hash(vOps, mOps, node);
    }

    /// Equality strategy for node id
    @Override
    public boolean equals(int a, int b) {
        if (a == b) return true;
        final var value = graph.getValue();
        final var memory = graph.getMemory();
        final var vOpsA = value.getOperands(a);
        final var vOpsB = value.getOperands(b);
        final var mOpsA = memory.getOperands(a);
        final var mOpsB = memory.getOperands(b);

        final var nodeA = graph.getNode(a);
        final var nodeB = graph.getNode(b);
        return Objects.equals(vOpsA, vOpsB) && Objects.equals(mOpsA, mOpsB) &&
                (((nodeA == nodeB) || (nodeA.getClass() == nodeB.getClass())) && nodeA.isEqual(nodeB));
    }
}
