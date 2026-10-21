package com.ishland.c2me.opts.dfc.common.ir.phase.scalar;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.LongUtil;
import com.ishland.c2me.opts.dfc.common.ir.graph.RPOIterator;
import com.ishland.c2me.opts.dfc.common.ir.graph.value.GraphUtil;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.phase.HighTierPhase;
import com.ishland.c2me.opts.dfc.common.ir.phase.MidTierPhase;
import it.unimi.dsi.fastutil.ints.*;

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
     * This phase utilizes a hash map of (incoming edges, node) and runs two
     * kinds of numbering back and forth until no more node needs to be numbered. <br/>
     * The graph is iterated in reverse post order: a node is processed after all
     * of its predecessors. For every node met, try to find a replacement in the map.
     * If a replacement exists, replace all usages of this node to that node. <br/>
     */
    @Override
    public void run() {
        final var value = graph.getValue();
        for (int i = 0; i < graph.countPrograms(); i++) {
            final long program = graph.getProgramAt(i);
            final int begin = LongUtil.getLow(program);
            final int end = LongUtil.getHigh(program);
            final var rpo = new RPOIterator(value, begin, end);
            while (rpo.hasNext()) {
                final int node = rpo.nextInt();
                if (!number(node)) continue;
                // Add to canonicalizer for dead code elimination
                enqueue(node);
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

        for (int j = 0; j < scheduling.size(); j++) {
            control.appendEdge(scheduling.getInt(j), replacement);
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
