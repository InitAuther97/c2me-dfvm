package com.ishland.c2me.opts.dfc.common.ir.phase.cfg;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.LongUtil;
import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeAccessor;
import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeList;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.graph.cfg.CFGInfo;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.branch.PhiNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.MidTierPhase;
import com.ishland.c2me.opts.dfc.common.util.FastCompareIntArrayList;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import java.util.BitSet;

// Phi is not touched in this stage because Phi is already pinned at Merge, which is an invariant.
public class GCM implements MidTierPhase {

    private static final ThreadLocal<Workspace> WORKSPACE_CACHE = ThreadLocal.withInitial(Workspace::new);

    record Workspace(IntArrayList list, EdgeList scheduling, ReferenceArrayList<IntArrayList> cache,
                     Object2ReferenceOpenHashMap<IntArrayList, IntList> optoCache, BitSet bitset) {
        Workspace() {
            this(new IntArrayList(), new EdgeList(), new ReferenceArrayList<>(), new Object2ReferenceOpenHashMap<>(), new BitSet());
        }
    }

    private final GraphContext context;
    private final IntArrayList stack;
    private final EdgeList scheduling;
    private final ReferenceArrayList<IntArrayList> cache;
    private final Object2ReferenceOpenHashMap<IntArrayList, IntList> optoCache;
    private final BitSet scheduledNodes;

    public GCM(GraphContext context) {
        this.context = context;
        final var workspace = WORKSPACE_CACHE.get();
        this.stack = workspace.list;
        this.scheduling = workspace.scheduling;
        this.cache = workspace.cache;
        this.optoCache = workspace.optoCache;
        this.scheduledNodes = workspace.bitset;
    }

    /// Assign all nodes to its latest used location.
    /// Then, do hoisting properly to reduce code size.
    /// Phi nodes are assigned to its respective Merge node.
    @Override
    public void run() {
        final var graph = context.getIrGraph();
        final int sizeCache = scheduling.size();
        final int sizeGraph = graph.size();
        if (sizeCache < sizeGraph) {
            scheduling.allocate(sizeGraph - sizeCache);
        }
        final var ctrl = graph.getControl();
        for (int i = 0; i < graph.countPrograms(); i++) {
            final var cfg = graph.getOrComputeCFGAt(i);
            cfg.walkInDominance((j, l) -> schedule(cfg, j, l));
            this.optimizeAndSet(cfg, ctrl);
        }
    }

    private void schedule(CFGInfo cfg, int index, long block) {
        // DFS array used to propagate node placement
        final var graph = context.getIrGraph();
        // There may be multiple blocks ending with Merge
        // But only one begins with Merge
        if (cfg.isMergeBlock(index)) {
            schedLateOnMerge(stack, graph, scheduling, cfg, scheduledNodes, index, block);
        }
        // Merge block can be the end block at the same time
        if (index == cfg.getEndBlock()) {
            schedLateOnReturn(stack, graph, scheduling, scheduledNodes, index, block);
        }
    }

    private void optimizeAndSet(CFGInfo cfg, EdgeAccessor ctrl) {
        for (int i = 0; i < scheduling.size(); i++) {
            final var scheduling = this.scheduling.getOperands(i);
            if (scheduling == IntLists.EMPTY_LIST) continue;
            final var result = this.optoCache.get((IntArrayList) scheduling);
            final IntList sched;
            if (result != null) {
                sched = result;
            } else {
                final IntArrayList keyList = cache.isEmpty() ? new FastCompareIntArrayList() : cache.pop();
                keyList.clear();
                keyList.addAll(scheduling);
                optimize(scheduling, cfg);
                optoCache.put(keyList, scheduling);
                sched = scheduling;
            }
            for (int j = 0; j < sched.size(); j++) {
                final int bbIdx = sched.getInt(j);
                final int nodeIdx = cfg.getBlockBegin(bbIdx);
                ctrl.appendEdge(nodeIdx, i);
            }
        }
        scheduling.resetUnsafe();
        cache.addAll(optoCache.keySet());
        optoCache.clear();
    }

    private static void optimize(IntList sched, CFGInfo cfg) {
        boolean changedGlobal = false;
        do {
            int order = Integer.MAX_VALUE, endOrder = Integer.MIN_VALUE;
            final var domTree = cfg.getDomTree();
            int delta = -1;
            for (int i = 0; i < sched.size(); i++) {
                final int control = sched.getInt(i);
                final int orderThis = domTree.getOrder(control);
                final int endOrderThis = domTree.getEndOrder(control);
                if (order <= orderThis && endOrderThis <= endOrder) {
                    if (delta == 0) continue;
                    sched.set(i + delta, sched.getInt(i));
                } else {
                    delta++;
                    order = orderThis;
                    endOrder = endOrderThis;
                }
            }

            final var pdomTree = cfg.getPdomTree();
            boolean changed = true;
            while (changed) {
                changed = false;
                for (int i = 0; i < sched.size(); i++) {
                    int ctrl1 = sched.getInt(i);
                    for (int j = i; j < sched.size(); j++) {
                        final int ctrl2 = sched.getInt(j);
                        if (!pdomTree.isDominatedBy(ctrl1, ctrl2)) continue;
                        ctrl1 = domTree.lca(ctrl1, ctrl2);
                        sched.set(i, ctrl1);
                        sched.removeInt(j);
                        changed = true;
                    }
                }
            }

            changed = true;
            while (changed) {
                changed = false;
                for (int i = 0; i < sched.size(); i++) {
                    int ctrl1 = sched.getInt(i);
                    for (int j = i; j < sched.size(); j++) {
                        final int ctrl2 = sched.getInt(j);
                        if (!cfg.isVeryBusy(ctrl1, ctrl2)) continue;
                        ctrl1 = domTree.lca(ctrl1, ctrl2);
                        sched.set(i, ctrl1);
                        sched.removeInt(j);
                        changed = true;
                    }
                }
            }

            for (int i = 0; i < sched.size(); i++) {
                int best = sched.getInt(i);
                int bestDepth = cfg.getLoopDepthForBlock(best);
                if (bestDepth != 0) {
                    int idom = best, depth;
                    while ((idom = domTree.getIDom(idom)) != -1) {
                        depth = cfg.getLoopDepthForBlock(idom);
                        if (depth < bestDepth) {
                            best = idom;
                            bestDepth = depth;
                            changedGlobal = true;
                        }
                    }
                }
                sched.set(i, best);
            }
        } while (changedGlobal);
    }

    private static void schedLateOnReturn(IntArrayList stack, IrGraph graph, EdgeList scheduling, BitSet visited, int ret, long blk) {
        final var retVals = graph.getValue().getOperands(LongUtil.getHigh(blk));
        // We start from return value, and then mark other aspects (e.g. memory)
        // This prevents us from
        for (int i = 1; i < retVals.size(); i++) {
            schedLateIterative(stack, graph, scheduling, visited, ret, retVals.getInt(i));
        }
        visited.clear();
    }

    private static void schedLateOnMerge(IntArrayList stack, IrGraph graph, EdgeList scheduling, CFGInfo cfg, BitSet visited, int merge, long blk) {
        final var ctrl = cfg.getCFG();
        final var value = graph.getValue();
        final var upstreamCtrl = ctrl.getOperands(merge);
        // We need pinned Phi assignment, don't use CFG here
        final var usages = graph.getControl().getUsages(LongUtil.getLow(blk));
        for (int j = 0; j < usages.size(); j++) {
            final int idx = usages.getInt(j);
            final var node2 = graph.getNode(idx);
            // Phi is already pinned at Merge.
            // This is guaranteed by the graph semantic.
            if (node2 != PhiNode.PHI) {
                continue;
            }
            final var upstreamPhi = value.getOperands(idx);
            final int valueZero = upstreamPhi.getInt(0);
            final int valueOne = upstreamPhi.getInt(1);
            final int branchZero = upstreamCtrl.getInt(0);
            final int branchOne = upstreamCtrl.getInt(1);
            schedLateIterative(stack, graph, scheduling, visited, branchZero, valueZero);
            visited.clear();
            schedLateIterative(stack, graph, scheduling, visited, branchOne, valueOne);
            visited.clear();
        }
    }

    private static void schedLateIterative(IntArrayList stack, IrGraph graph, EdgeList scheduling, BitSet visited, int control, int valueToSchedule) {
        final var value = graph.getValue();
        stack.push(valueToSchedule);
        while (!stack.isEmpty()) {
            int toSchedule = stack.popInt();
            if (visited.get(toSchedule)) continue;
            visited.set(toSchedule);
            final var node = graph.getNode(toSchedule);
            if (node == PhiNode.PHI) continue;
            scheduling.appendEdge(control, toSchedule);
            final var operands = value.getOperands(toSchedule);
            stack.addAll(operands);
        }
    }
}
