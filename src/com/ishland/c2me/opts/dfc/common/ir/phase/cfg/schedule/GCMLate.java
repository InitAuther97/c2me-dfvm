package com.ishland.c2me.opts.dfc.common.ir.phase.cfg.schedule;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.LongUtil;
import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeAccessor;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.graph.cfg.CFGInfo;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.MergeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ReturnNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.memory.MemPhiNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.branch.PhiNode;
import com.ishland.flowsched.util.Assertions;
import it.unimi.dsi.fastutil.ints.*;

import java.util.BitSet;

// Phi is not touched in this stage because Phi is already pinned at Merge, which is an invariant.
public class GCMLate implements SchedulePhase {

    private static final ThreadLocal<Workspace> WORKSPACE_CACHE = ThreadLocal.withInitial(Workspace::new);

    record Workspace(IntArrayList list, BitSet bitset) {
        Workspace() {
            this(new IntArrayList(), new BitSet());
        }
    }
    private final IntArrayList stack;
    private final BitSet availableNodes;

    public GCMLate() {
        final var workspace = WORKSPACE_CACHE.get();
        this.stack = workspace.list;
        this.availableNodes = workspace.bitset;
    }

    class ScopeBoundScheduler implements EdgeAccessor.DepthFirstVisitor {

        private final GraphContext context;
        private final CFGInfo cfg;
        private final BitSet[] scheduling;
        private final BitSet visited = new BitSet();

        ScopeBoundScheduler(GraphContext context, CFGInfo cfg, BitSet[] scheduling) {
            this.context = context;
            this.scheduling = scheduling;
            this.cfg = cfg;
        }

        @Override
        public boolean enter(int bbIdx) {
            if (visited.get(bbIdx)) return false;
            visited.set(bbIdx);
            schedule(context, scheduling[bbIdx], cfg.getBasicBlock(bbIdx));
            return true;
        }

        @Override
        public void leave(int node) {
            availableNodes.andNot(scheduling[node]);
        }
    }

    /// Assign all nodes to its latest used location.
    /// Phi nodes are assigned to its respective Merge node.
    /// Input of Phi nodes are assigned to its respective branch begin node.
    @Override
    public void schedule(GraphContext context, int i, BitSet[] scheduling) {
        final var graph = context.getIrGraph();
        final var cfg = graph.getOrComputeCFGAt(i);
        final var visitor = new ScopeBoundScheduler(context, cfg, scheduling);
        cfg.getDomTree().walk(visitor);
    }

    private void schedule(GraphContext context, BitSet scheduling, long block) {
        final var graph = context.getIrGraph();
        final var control = graph.getControl();
        final int beginBlockNodeId = LongUtil.getLow(block);
        final var beginBlockNode = graph.getNode(beginBlockNodeId);
        if (beginBlockNode == MergeNode.MERGE) {
            final var phis = control.getUsages(beginBlockNodeId);
            for (int i = 0; i < phis.size(); i++) {
                final int phi = phis.getInt(i);
                availableNodes.set(phi);
                scheduling.set(phi);
            }
        }
        final int endBlockNodeId = LongUtil.getHigh(block);
        final var endBlockNode = graph.getNode(endBlockNodeId);
        if (endBlockNode == MergeNode.MERGE) {
            final var phis = control.getUsages(endBlockNodeId);
            final int branch = control.getOperands(endBlockNodeId).indexOf(beginBlockNodeId);
            for (int i = 0; i < phis.size(); i++) {
                final int phiOrMemPhiId = phis.getInt(i);
                final var phiOrMemPhi = graph.getNode(phiOrMemPhiId);
                Assertions.assertTrue(phiOrMemPhi == PhiNode.PHI || phiOrMemPhi == MemPhiNode.PHI, "Expected only PhiNode or MemPhiNode scheduled before global code motion");
                final int nodeToSchedule;
                if (phiOrMemPhi == PhiNode.PHI) {
                    nodeToSchedule = graph.getValue().getOperands(phiOrMemPhiId).getInt(branch);
                } else {
                    nodeToSchedule = graph.getMemory().getOperands(phiOrMemPhiId).getInt(branch);
                }
                schedLateIterative(graph, scheduling, nodeToSchedule);
            }
        } else if (endBlockNode == ReturnNode.RET) {
            schedLateOnReturn(graph, scheduling, endBlockNodeId);
        }
    }

    private void schedLateOnReturn(IrGraph graph, BitSet scheduling, int ebbIdx) {
        final var values = graph.getValue().getOperands(ebbIdx);
        final var memories = graph.getMemory().getOperands(ebbIdx);
        Assertions.assertTrue(values.size() == 1, "Should have only one return value");
        schedLateIterative(graph, scheduling, values.getInt(0));
        for (int i = 0; i < memories.size(); i++) {
            schedLateIterative(graph, scheduling, memories.getInt(i));
        }
    }

    private void schedLateIterative(IrGraph graph, BitSet scheduling, int nodeToSchedule) {
        final var stack = this.stack;
        final var memory = graph.getMemory();
        final var value = graph.getValue();
        stack.push(nodeToSchedule);
        while (!stack.isEmpty()) {
            int toSchedule = stack.popInt();
            if (availableNodes.get(toSchedule)) continue;
            availableNodes.set(toSchedule);
            scheduling.set(toSchedule);
            final var vOps = value.getOperands(toSchedule);
            final var mOps = memory.getOperands(toSchedule);
            stack.addAll(vOps);
            stack.addAll(mOps);
        }
    }
}
