package com.ishland.c2me.opts.dfc.common.ir.phase.cfg;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeAccessor;
import com.ishland.c2me.opts.dfc.common.ir.graph.PostOrderVisitor;
import com.ishland.c2me.opts.dfc.common.ir.graph.cfg.CFGAccess;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import java.util.BitSet;

public class GVNPRE implements SchedulePhase {

    private final GraphContext ctx;

    public GVNPRE(GraphContext ctx) {
        this.ctx = ctx;
    }

    public void schedule(GraphContext graph, int programIndex, BitSet[] scheduling) {
        final var cfg = graph.getIrGraph().getOrComputeCFGAt(programIndex, CFGAccess.Status.DOMINATOR);
        final var dom = cfg.getDomTree();
        final var anticIn = new BitSet[scheduling.length];
        final var availOut = new BitSet[scheduling.length];
        for (int i = 0; i < scheduling.length; i++) {
            anticIn[i] = new BitSet();
            availOut[i] = new BitSet();
        }

        // Step.1 BuildSets

        // First, build AVAIL_OUT: values computed at the end of this block.
        // It can be computed by appending everything calculated to idom's AVAIL_OUT.
        // This requires a top-down traversal of dominator tree.
        cfg.getDomTree().walk(new AvailOutVisitor(scheduling, availOut));

        // Then, build ANTIC_IN: values needed at the beginning of this block.
        // It can be computed by intersecting all successors' ANTIC_IN
        // and appending everything calculated in this block into it.
        // The end block has no successor and its ANTIC_IN is everything it computes.

        // This requires a crafted post-order traversal of CFG,
        // which always walk the back edge until the loop ANTIC_IN reaches fixpoint.
        // Since our phi_translate is an identity function, this reaches fixpoint
        // after taking the back edge exactly once for every loop structure.

        // However, such logic introduces excessive control flow and may not be fast
        // as bit operations are usually very cheap. Here we just iterate globally.
        // Estimated count to reach fixpoint is 2^max{lDepth} when loops contain
        // only one latch.
        final var anticInVisitor = new AnticInVisitor(cfg, scheduling, anticIn);
        final var flowGraph = cfg.getCFG();
        while (anticInVisitor.flip()) {
            flowGraph.walk(0, anticInVisitor);
        }

        // Step.2 Insert
        // If a node is anticipated at merge and available on one incoming edge,
        // insert it at the LCA of both blocks. (TODO insert at the other branch
        // This makes the downstream calculation fully redundant.
        // TODO: I think if we use phi to merge same calculation among two branches,
        // TODO: It's a problem how we implement proper optimization
        // TODO: We might need more change on the "pseudo" phi node we may use
        final var buffer = new BitSet();
        final var merges = cfg.mergeBlockIterator();
        while (merges.hasNext()) {
            int i = merges.nextInt();
            buffer.or(anticIn[i]);
            final var operands = flowGraph.getOperands(i);
            final int first = operands.getInt(0), second = operands.getInt(1);
            final int lca = dom.lca(first, second);
            final var sched = scheduling[lca];
            buffer.and(availOut[first]);
            sched.or(buffer);
            buffer.clear();
            buffer.or(anticIn[second]);
            buffer.and(availOut[second]);
            sched.or(buffer);
            buffer.clear();
        }

        // Step.3 Eliminate
        // Traverse the dominator tree and eliminate all already scheduled node.
        // Note that this may eliminate previous inserted calculations.
        dom.walk(new EliminateVisitor(scheduling));
    }

    @Override
    public void run() {
        final var graph = ctx.getIrGraph();
        for (int i = 0; i < graph.countPrograms(); i++) {
            final var scheduling = collectNodeScheduling(ctx, i);
            schedule(ctx, i, scheduling);
            applyNodeScheduling(ctx, i, scheduling);
        }
    }

    static class AvailOutVisitor implements EdgeAccessor.DepthFirstVisitor {

        private final ReferenceArrayList<BitSet> stack = new ReferenceArrayList<>();
        private final BitSet[] scheduling, availOut;

        AvailOutVisitor(BitSet[] scheduling, BitSet[] availOut) {
            this.scheduling = scheduling;
            this.availOut = availOut;
        }

        @Override
        public boolean enter(int node) {
            final var availOut = this.availOut[node];
            stack.push(scheduling[node]);
            for (int i = 0; i < stack.size(); i++) {
                availOut.or(stack.get(i));
            }
            return true;
        }

        @Override
        public void leave(int node) {
            stack.pop();
        }
    }

    static class AnticInVisitor extends PostOrderVisitor {

        private final CFGAccess.Dominator info;
        private final BitSet[] scheduling;
        private final BitSet[] anticIn;
        private final BitSet dirty = new BitSet();
        private final BitSet buffer = new BitSet();
        boolean changed = true;

        AnticInVisitor(CFGAccess.Dominator info, BitSet[] scheduling, BitSet[] anticIn) {
            this.info = info;
            this.scheduling = scheduling;
            this.anticIn = anticIn;
            dirty.set(0, scheduling.length - 1);
        }

        boolean flip() {
            try {
                return changed;
            } finally {
                super.reset();
                changed = false;
            }
        }

        @Override
        public void leave(int node) {
            final var buffer = this.buffer;
            final var cfg = this.info.getCFG();
            final var dirty = this.dirty;
            if (!dirty.get(node)) return;
            dirty.clear(node);
            final var usages = cfg.getUsages(node);
            if (!usages.isEmpty()) {
                buffer.or(anticIn[usages.getInt(node)]);
                for (int i = 1; i < usages.size(); i++) {
                    buffer.and(anticIn[usages.getInt(node)]);
                }
            }
            buffer.or(this.scheduling[node]);
            final var original = this.anticIn[node];
            if (!original.equals(buffer)) {
                original.clear();
                original.or(buffer);
                final var operands = cfg.getOperands(node);
                for (int i = 0; i < operands.size(); i++) {
                    dirty.set(operands.getInt(i));
                }
            }
            buffer.clear();
        }
    }

    static class EliminateVisitor implements EdgeAccessor.DepthFirstVisitor {

        private final BitSet scheduledNodes = new BitSet();
        private final BitSet[] scheduling;

        EliminateVisitor(BitSet[] scheduling) {
            this.scheduling = scheduling;
        }

        @Override
        public boolean enter(int node) {
            scheduling[node].andNot(scheduledNodes);
            scheduledNodes.or(scheduling[node]);
            return true;
        }

        @Override
        public void leave(int node) {
            scheduledNodes.andNot(scheduling[node]);
        }
    }
}
