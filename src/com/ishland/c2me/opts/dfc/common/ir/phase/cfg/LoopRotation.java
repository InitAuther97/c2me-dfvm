package com.ishland.c2me.opts.dfc.common.ir.phase.cfg;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.LongUtil;
import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeAccessor;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.graph.cfg.CFGAccess;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.BranchBeginNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ControlNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.IfBranchNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.MergeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.memory.MemPhiNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.memory.MemProxyNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.branch.PhiNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.proxy.ValueProxyNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.RunCanonicalizerPhase;
import com.ishland.flowsched.util.Assertions;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ReferenceFunction;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import java.util.BitSet;

/// This phase performs loop rotation on all loops that need a rotation.
///
/// A loop that needs a rotation is defined as one that has an exit check
/// in the end of its header block and a basic block on its back edge.
///
/// Due to the lazy nature of late scheduling, values flowing back to the
/// phi are always scheduled on the back edge, to defer computations.
/// Redundant computations on back edge are not discovered by GVNPRE since
/// the loop can run this block for zero times, violating optimality.
///
/// We resolve this by guarding that the loop run for at least once. We
/// "rotate" the loop, peeling the exit check out as a guard, and insert it
/// at the last on the back edge, ensuring the original block is executed
/// for at least once, therefore enabling LICM for GVNPRE. We also need to
/// insert a Merge block because the original destination for exit block
/// now has 2 incoming edges, 1 from guard and 1 from the new exit block.
///
/// A case is that the computation is also used in loop check, which makes
/// the node scheduled earlier in the loop, leaving back edge a no-op.
/// In this case, rotation is not needed.
///
/// Since rotation inserts new nodes and control flows, we need to run
/// canonicalizer to find crucial optimization, such as constant condition
/// for a control flow, enabling the elimination of the loop guard. This is
/// usually observed on counted loops, whose rotation is theoretically free.
public class LoopRotation extends RunCanonicalizerPhase implements SchedulePhase {

    private static final Long2ReferenceFunction<IntArrayList> LIST_INIT = _ -> new IntArrayList();

    private final GraphContext ctx;
    public LoopRotation(GraphContext ctx) {
        super(ctx);
        this.ctx = ctx;
    }

    @Override
    public void run() {
        // We can't use collect and apply scheduling here as we actually modify CFG later.
        final var graph = ctx.getIrGraph();
        for (int i = 0; i < graph.countPrograms(); i++) {
            // Rotating a loop invalidates block indices, so recompute the CFG
            // after each rotation until no qualified loop remains.
            while (rotateFirstQualified(graph, i)) {
                graph.invalidateCFGAt(i);
                canonicalize();
            }
        }
    }

    private boolean rotateFirstQualified(IrGraph graph, int programIdx) {
        // We compute LoopBody here. This provides us with all blocks we need to inspect.
        final var cfg = graph.getOrComputeCFGAt(programIdx, CFGAccess.Status.LOOP_BODY);
        final var flowGraph = cfg.getCFG();
        final var control = graph.getControl();
        final var iter = cfg.loopHeaderIterator();
        int headerIdx, bodyIdx;
        selection:
        {
            while (iter.hasNext()) {
                headerIdx = iter.nextInt();
                // If not split, disqualify
                // The header block must end with an exit check
                if (!cfg.isIdxSplitBlock(headerIdx)) continue;
                final var loop = cfg.getLoopFromHeader(headerIdx);
                final var downstream = flowGraph.getUsages(headerIdx);
                final int firstD = downstream.getInt(0), secondD = downstream.getInt(1);
                final boolean firstExits = loop.exits().get(firstD);
                final boolean secondExits = loop.exits().get(secondD);
                // If none exit or both exit, disqualify
                Assertions.assertTrue(!(firstExits && secondExits), "Cannot be both exit and exit");
                if (!firstExits && !secondExits) continue;
                bodyIdx = firstExits ? secondD : firstD;
                final int backEdge = loop.latch();
                final int beginNode = cfg.getBlockBegin(backEdge);
                // Back edge block is empty, disqualify
                if (control.getUsages(beginNode).size() == 1) continue;
                break selection;
            }
            return false;
        }
        rotate(graph, cfg, headerIdx, bodyIdx);
        return true;
    }

    private static void pushOrDefer(IrGraph graph, LongArrayList stack, int idx, int parent, BitSet ready, BitSet header) {
        final var mem = graph.getMemory();
        final var val = graph.getValue();
        final var mOps = mem.getOperands(idx);
        final var vOps = val.getOperands(idx);
        for (int i = 0; i < mOps.size(); i++) {
            if (!ready.get(i) && !header.get(idx)) return;
        }
        for (int i = 0; i < vOps.size(); i++) {
            if (!ready.get(i) && !header.get(idx)) return;
        }
        stack.push(LongUtil.make(idx, parent));
    }

    private static void translateHeaderPhi(IrGraph graph, IntArrayList keys, ReferenceArrayList<IntArrayList> values, int phi) {
        final var edges = phi < 0 ? graph.getMemory() : graph.getValue();
        keys.add(phi);
        keys.add(edges.getOperands(Math.abs(phi)).getInt(0));
    }

    private static void translateAndRegister(IrGraph graph, IntArrayList keys, ReferenceArrayList<IntArrayList> values, int node, int ctrl) {
        final int idx = keys.indexOf(node);
        Assertions.assertTrue(idx < 0, "Operand is already there when translated");
        keys.add(node);
        final var oldNode = graph.getNode(node);
        final int newNode = graph.addNode(oldNode);
        keys.add(newNode);
        graph.getControl().appendEdge(ctrl, newNode);
        final var mOps = graph.getMemory().getOperands(node);
        final var vOps = graph.getValue().getOperands(node);
        for (int i = 0; i < vOps.size(); i++) {
            final int orig = vOps.getInt(i);
            final int translated = lookupTranslation(keys, orig);
            graph.getValue().appendEdge(translated >= 0 ? translated : orig, newNode);
        }
        for (int i = 0; i < mOps.size(); i++) {
            final int orig = mOps.getInt(i);
            final int translated = lookupTranslation(keys, -orig);
            graph.getMemory().appendEdge(translated >= 0 ? translated : orig, newNode);
        }
        values.add(null);
    }

    private static int lookupTranslation(IntArrayList keys, int original) {
        final int result = keys.indexOf(original);
        if (result < 0) return -1;
        return keys.getInt(result + 1);
    }

    private static void putUsage(IntArrayList keys, ReferenceArrayList<IntArrayList> values, int operand, int usage) {
        final int idx = keys.indexOf(operand);
        IntArrayList list;
        Assertions.assertTrue((idx & 1) == 0, "Find operand in the place of translatedOperand");
        if (idx >= 0) {
            list = values.get(idx >> 1);
            if (list == null) {
                list = new IntArrayList(4);
                values.set(idx, list);
            }
        } else {
            keys.add(operand);
            keys.add(-1);
            list = new IntArrayList(4);
            values.add(list);
        }
        list.add(usage);
    }

    private static void collectReadyNodes(IrGraph graph, CFGAccess.LoopBody cfg, int blockIdx, BitSet readyNodes) {
        final var dom = cfg.getDomTree();
        final int begin = 0;
        int current = blockIdx;
        while ((current = dom.getIDom(current)) != begin) {
            final int beginNode = cfg.getBlockBegin(current);
            final var list = graph.getControl().getUsages(beginNode);
            for (int i = 0; i < list.size(); i++) {
                final int node =  list.getInt(i);
                readyNodes.set(node);
            }
        }
    }

    private static void rotate(IrGraph graph, CFGAccess.LoopBody cfg, int headerIdx, int bodyIdx) {
        final var value = graph.getValue();
        final var mem = graph.getMemory();
        final var ctrl = graph.getControl();
        final int beginNode = cfg.getBlockBegin(headerIdx);
        final var scheduling = ctrl.getUsages(beginNode);
        final var copy = new IntArrayList(scheduling);
        final var keys = new IntArrayList(8);
        final var values = new ReferenceArrayList<IntArrayList>(4);
        final var stack = new LongArrayList(8);
        final var readyNodes = new BitSet();
        final var inHeader = new BitSet();
        collectReadyNodes(graph, cfg, beginNode, readyNodes);
        for (int i = 1; i < copy.size(); i++) { // exclude control usage
            final int idx = copy.getInt(i);
            final var node = graph.getNode(idx);
            if (node == MemPhiNode.PHI) {
                translateHeaderPhi(graph, keys, values, -idx);
                inHeader.set(idx);
                readyNodes.set(idx);
            } else if (node == PhiNode.PHI) {
                translateHeaderPhi(graph, keys, values, idx);
                inHeader.set(idx);
                readyNodes.set(idx);
            } else {
                pushOrDefer(graph, stack, idx, -1, readyNodes, inHeader);
            }
        }
        final var loop = cfg.getLoopFromHeader(headerIdx);
        final int headerNode = cfg.getBlockBegin(headerIdx);
        final var bodies = loop.blocks();
        while (!stack.isEmpty()) {
            final long top = stack.popLong();
            final int idx = LongUtil.getLow(top);
            final int parent = LongUtil.getHigh(top);
            final var node = graph.getNode(idx);
            if (node == ValueProxyNode.PROXY || node == MemProxyNode.PROXY) {
                // This is a use outside of loop
                putUsage(keys, values, parent, -idx);
                continue;
            }
            final var sched = ctrl.getOperands(idx);
            Assertions.assertTrue(!sched.isEmpty(), "Found node not scheduled to anywhere");
            final int firstSched = sched.getInt(0);
            if (firstSched == headerIdx) {
                // Prevent reentrant
                if (inHeader.get(idx)) continue;
                // ensure topological order
                inHeader.set(idx);
                translateAndRegister(graph, keys, values, idx, headerNode);
                // This is inside header, propagate
                final var children = value.getUsages(idx);
                final var children2 = mem.getUsages(idx);
                // we use idx with extra signs
                // because we need to specify the edge type
                for (int j = 0; j < children.size(); j++)
                    pushOrDefer(graph, stack, children.getInt(j), idx, readyNodes, inHeader);
                for (int j = 0; j < children2.size(); j++)
                    pushOrDefer(graph, stack, children2.getInt(j), idx, readyNodes, inHeader);
                continue;
            }
            // This should be inside loop but outside header
            for (int i = 0; i < sched.size(); i++) {
                final int nodeIdx = sched.getInt(i);
                final int blockIdx = cfg.getBlockIdxForBeginNode(nodeIdx);
                Assertions.assertTrue(bodies.get(blockIdx), "Usage outside loop is not behind ProxyNode");
            }
            putUsage(keys, values, parent, idx);
        }

        // Insert a Merge node at the beginning of body block
        final int endNode = cfg.getBlockEnd(headerIdx);
        final int bodyBeginNode = cfg.getBlockBegin(bodyIdx);
        final int newMerge = graph.addNode(MergeNode.MERGE);
        ctrl.insertEdgeMiddleman(endNode, bodyBeginNode, newMerge);

        // Create Phis for respective keys and wire them at the body block
        // Wire all usages onto the corresponding Phis
        // Some nodes may have both memory and value produced
        for (int i = 0; i < keys.size() >>   1; i++) {
            final int key = keys.getInt(i << 1);
            final int translated = keys.getInt((i << 1) + 1);
            final int phi = graph.addNode(key >= 0 ? PhiNode.PHI : MemPhiNode.PHI);
            ctrl.appendEdge(newMerge, phi);

        }
    }

    /// Thrown when the condition slice cannot be duplicated safely, e.g.
    /// when it contains a phi pinned at another merge inside the loop.
    private static final class Bailout extends Exception {
        private Bailout() {}
    }

    private final class Rotation {

        private final IrGraph graph;
        private final CFGAccess.LoopBody cfg;
        private final EdgeAccessor control, value, memory;

        private final int headerIdx, exitIdx, bodyIdx;
        private final int headerNode, headerIf, cond, latchBegin, exitBranchNode, exitBranchEnd, loopBody, loopBodyEnd, parentBegin;
        private final int parentIdx, latchIdx;
        private final boolean exitOnTrue;

        private final Int2IntOpenHashMap beginToBlock = new Int2IntOpenHashMap();
        private final Int2IntOpenHashMap endToBlock = new Int2IntOpenHashMap();
        private final Int2IntOpenHashMap guardMemo = new Int2IntOpenHashMap();
        private final Int2IntOpenHashMap latchMemo = new Int2IntOpenHashMap();
        private final Int2IntOpenHashMap phiV = new Int2IntOpenHashMap();
        private final Int2IntOpenHashMap phiM = new Int2IntOpenHashMap();
        private final BitSet tainted = new BitSet();
        private final IntArrayList created = new IntArrayList();
        private final IntArrayList repairN = new IntArrayList();
        private final IntArrayList repairU = new IntArrayList();
        private final IntArrayList repairMem = new IntArrayList();

        private int guardCond, latchCond, NewMerge;

        Rotation(IrGraph graph, CFGAccess.LoopBody cfg, int headerIdx, int exitIdx, int bodyIdx) {
            this.graph = graph;
            this.cfg = cfg;
            this.control = graph.getControl();
            this.value = graph.getValue();
            this.memory = graph.getMemory();
            this.exitIdx = exitIdx;
            this.headerIdx = headerIdx;
            this.bodyIdx = bodyIdx;
            this.beginToBlock.defaultReturnValue(-1);
            this.endToBlock.defaultReturnValue(-1);
            this.guardMemo.defaultReturnValue(-1);
            this.latchMemo.defaultReturnValue(-1);
            this.phiV.defaultReturnValue(-1);
            this.phiM.defaultReturnValue(-1);
            final int blocks = cfg.countBasicBlocks();
            for (int i = 0; i < blocks; i++) {
                beginToBlock.put(cfg.getBlockBegin(i), i);
                endToBlock.put(cfg.getBlockEnd(i), i);
            }
            this.headerNode = cfg.getBlockBegin(headerIdx);
            this.headerIf = cfg.getBlockEnd(headerIdx);
            this.cond = value.getOperands(headerIf).getInt(0);
            final var loop = cfg.getLoopFromHeader(headerIdx);
            this.latchBegin = cfg.getBlockBegin(loop.latch());
            this.exitBranchNode = cfg.getBlockBegin(exitIdx);
            this.exitBranchEnd = cfg.getBlockEnd(exitIdx);
            final var ifUsages = control.getUsages(headerIf);
            // Usages of an if are ordered: TRUE branch first, then FALSE
            this.exitOnTrue = ifUsages.getInt(0) == exitBranchNode;
            this.loopBody = ifUsages.getInt(exitOnTrue ? 1 : 0);
            this.loopBodyEnd = control.getUsages(loopBody).getInt(0);
            final var hOps = control.getOperands(headerNode);
            this.latchIdx = hOps.indexOf(latchBegin);
            Assertions.assertTrue(latchIdx >= 0, "");
            this.parentIdx = latchIdx < 0 ? -1 : 1 - latchIdx;
            this.parentBegin = parentIdx < 0 ? -1 : hOps.getInt(parentIdx);
            // An empty body: the header's body edge is itself the back edge.
            // The latch block then merges into the header block instead.
        }

        boolean run() {
            try {
                computeTaint();
                // Duplicate condition nodes used, translating the phi to the
                // input of the corresponding edge
                guardCond = translate(cond, false);
                latchCond = translate(cond, true);
                NewMerge = graph.addNode(MergeNode.MERGE);
                created.add(NewMerge);
                scanRepairs();
                createRepairPhis();
                // No bailout may happen beyond this point
            } catch (Bailout b) {
                for (int i = 0; i < created.size(); i++) {
                    enqueue(created.getInt(i));
                }
                return false;
            }
            rewire();
            applyRepairs();
            enqueueTouched();
            return true;
        }

        /// Categorize nodes in three kinds: first, nodes scheduled in header;
        /// second, nodes dominated by the True branch of the node
        /// third, nodes dominated by the False branch of the node
        private void resolveHeader() {

        }

        /// Nodes scheduled inside the loop whose computation transitively
        /// depends on a phi pinned at the loop header. Such nodes evaluate
        /// to a different value on each iteration, so the guard and the
        /// latch check each need their own copy with the phi translated;
        /// untainted nodes are shared.
        private void computeTaint() {
            final var stack = new IntArrayList();
            final var hUsages = control.getUsages(headerNode);
            for (int i = 0; i < hUsages.size(); i++) {
                final int n = hUsages.getInt(i);
                final var node = graph.getNode(n);
                if (node instanceof PhiNode || node instanceof MemPhiNode) stack.push(n);
            }
            while (!stack.isEmpty()) {
                final int n = stack.popInt();
                if (tainted.get(n)) continue;
                tainted.set(n);
                stack.addAll(value.getUsages(n));
                stack.addAll(memory.getUsages(n));
            }
        }

        /// Duplicate the slice of {@code v}, substituting phis pinned at the
        /// loop header with the input flowing through the preheader edge
        /// (guard copy) or the back edge (latch copy). Duplicated nodes are
        /// scheduled into the preheader resp. latch block. Untainted nodes
        /// already available at the destination are reused, not duplicated.
        private int translate(int v, boolean latchDir) throws Bailout {
            final var memo = latchDir ? latchMemo : guardMemo;
            final int cached = memo.get(v);
            if (cached != -1) return cached;
            final var node = graph.getNode(v);
            if (node == null) throw new Bailout();
            final int dest = latchDir ? latchBegin : parentBegin;
            if (tainted.get(v)) {
                if (node instanceof PhiNode || node instanceof MemPhiNode) {
                    final var cOps = control.getOperands(v);
                    if (!cOps.isEmpty() && cOps.getInt(0) == headerNode) {
                        final var edge = node instanceof MemPhiNode ? memory : value;
                        final int result = edge.getOperands(v).getInt(latchDir ? latchIdx : parentIdx);
                        memo.put(v, result);
                        return result;
                    }
                    // A phi pinned at another merge inside the loop cannot be
                    // duplicated; rotating this loop is not possible
                    throw new Bailout();
                }
                if (node instanceof ControlNode) throw new Bailout();
            } else {
                if (node instanceof ControlNode) throw new Bailout();
                final var cOps = control.getOperands(v);
                if (cOps.isEmpty()) {
                    memo.put(v, v);
                    return v;
                }
                final int schedBegin = cOps.getInt(0);
                if (schedBegin == dest || dominates(schedBegin, dest)) {
                    memo.put(v, v);
                    return v;
                }
                // Pinned phis cannot be duplicated
                if (node instanceof PhiNode || node instanceof MemPhiNode) throw new Bailout();
            }
            // It is OK to allocate with instances already in the graph: nodes
            // are value based and immutable, and GVN deduplicates them later
            final int dup = graph.addNode(node);
            created.add(dup);
            memo.put(v, dup);
            control.appendEdge(dest, dup);
            final var vOps = value.getOperands(v);
            for (int i = 0; i < vOps.size(); i++) {
                value.appendEdge(translate(vOps.getInt(i), latchDir), dup);
            }
            final var mOps = memory.getOperands(v);
            for (int i = 0; i < mOps.size(); i++) {
                memory.appendEdge(translate(mOps.getInt(i), latchDir), dup);
            }
            return dup;
        }

        private boolean dominates(int beginA, int beginB) {
            final int ai = beginToBlock.get(beginA), bi = beginToBlock.get(beginB);
            return ai >= 0 && bi >= 0 && cfg.isBlockDominatedBy(bi, ai);
        }

        /// Values scheduled on the header or the exit block no longer dominate
        /// their users past the exit once the exit splits into the guard and
        /// latch paths. Scan for such usages; each is later rewired to a phi
        /// pinned at the new merge, merging the guard-side and latch-side
        /// translations of the value.
        private void scanRepairs() {
            final var hUsages = control.getUsages(headerNode);
            for (int i = 0; i < hUsages.size(); i++) {
                final int n = hUsages.getInt(i);
                if (n == headerIf || graph.getNode(n) instanceof ControlNode) continue;
                collectRepairs(n, false);
                collectRepairs(n, true);
            }
            final var xUsages = control.getUsages(exitBranchNode);
            for (int i = 0; i < xUsages.size(); i++) {
                final int n = xUsages.getInt(i);
                if (n == exitBranchEnd) continue;
                collectRepairs(n, false);
                collectRepairs(n, true);
            }
        }

        private void collectRepairs(int n, boolean mem) {
            final var edge = mem ? memory : value;
            final var usages = edge.getUsages(n);
            for (int i = 0; i < usages.size(); i++) {
                final int u = usages.getInt(i);
                if (u == headerIf) continue;
                final var uNode = graph.getNode(u);
                if (uNode == null) continue;
                if (uNode instanceof PhiNode || uNode instanceof MemPhiNode) {
                    // For phis only inputs flowing along edges dominated by
                    // the old exit block need a repair
                    final var cOps = control.getOperands(u);
                    if (cOps.size() != 1) continue;
                    final var mOps = control.getOperands(cOps.getInt(0));
                    final var inputs = edge.getOperands(u);
                    for (int k = 0; k < inputs.size() && k < mOps.size(); k++) {
                        if (inputs.getInt(k) != n) continue;
                        final int pb = beginToBlock.get(mOps.getInt(k));
                        if (pb >= 0 && cfg.isBlockDominatedBy(pb, exitIdx)) recordRepair(n, u, mem);
                    }
                } else if (uNode instanceof ControlNode) {
                    if (u == exitBranchEnd) {
                        recordRepair(n, u, mem);
                        continue;
                    }
                    final int ub = endToBlock.get(u);
                    if (ub >= 0 && ub != exitIdx && cfg.isBlockDominatedBy(ub, exitIdx)) recordRepair(n, u, mem);
                } else {
                    final var cOps = control.getOperands(u);
                    if (cOps.size() != 1) continue;
                    final int ub = beginToBlock.get(cOps.getInt(0));
                    if (ub >= 0 && ub != exitIdx && cfg.isBlockDominatedBy(ub, exitIdx)) recordRepair(n, u, mem);
                }
            }
        }

        private void recordRepair(int n, int u, boolean mem) {
            repairN.add(n);
            repairU.add(u);
            repairMem.add(mem ? 1 : 0);
        }

        private void createRepairPhis() throws Bailout {
            for (int i = 0; i < repairN.size(); i++) {
                final int n = repairN.getInt(i);
                final boolean mem = repairMem.getInt(i) != 0;
                final var map = mem ? phiM : phiV;
                if (map.containsKey(n)) continue;
                final var edge = mem ? memory : value;
                final int phi = graph.addNode(mem ? MemPhiNode.PHI : PhiNode.PHI);
                created.add(phi);
                map.put(n, phi);
                control.appendEdge(NewMerge, phi);
                edge.appendEdge(translate(n, false), phi);
                edge.appendEdge(translate(n, true), phi);
            }
        }

        private void rewire() {
            final int guardIf = graph.addNode(IfBranchNode.BRANCH);
            final int gexit = graph.addNode(exitOnTrue ? BranchBeginNode.TRUE : BranchBeginNode.FALSE);
            final int gbody = graph.addNode(exitOnTrue ? BranchBeginNode.FALSE : BranchBeginNode.TRUE);
            final int lexit = graph.addNode(exitOnTrue ? BranchBeginNode.TRUE : BranchBeginNode.FALSE);
            final int lback = graph.addNode(exitOnTrue ? BranchBeginNode.FALSE : BranchBeginNode.TRUE);
            created.add(guardIf);
            created.add(gexit);
            created.add(gbody);
            created.add(lexit);
            created.add(lback);

            // Insert guard before the loop: the preheader now ends with the
            // peeled exit check, and the header is only reachable through it
            control.replaceEdge(headerNode, parentBegin, gbody);
            control.addEdgePair(parentBegin, 0, guardIf, 0);
            value.appendEdge(guardCond, guardIf);
            if (exitOnTrue) {
                control.appendEdge(guardIf, gexit);
                control.appendEdge(guardIf, gbody);
            } else {
                control.appendEdge(guardIf, gbody);
                control.appendEdge(guardIf, gexit);
            }
            control.appendEdge(gexit, NewMerge);

            // Remove the previous loop check and insert it at the last on the
            // back edge, reusing the old if node. Block-end edges must stay
            // at usages index 0, hence addEdgePair instead of replaceEdge.
            control.removeEdge(headerNode, headerIf);
            control.addEdgePair(latchBegin, 0, headerIf, 0);
            value.replaceEdge(headerIf, cond, latchCond);
            control.removeEdge(headerIf, exitBranchNode);
            control.removeEdge(headerIf, loopBody);
            if (exitOnTrue) {
                control.appendEdge(headerIf, lexit);
                control.appendEdge(headerIf, lback);
            } else {
                control.appendEdge(headerIf, lback);
                control.appendEdge(headerIf, lexit);
            }
            control.replaceEdge(headerNode, latchBegin, lback);
            control.appendEdge(lexit, NewMerge);

            // The header block now falls through into the body
            final int iB = control.getOperands(loopBodyEnd).indexOf(loopBody);
            control.removeEdge(loopBody, loopBodyEnd);
            control.addEdgePair(headerNode, 0, loopBodyEnd, iB);
            final var usages = new IntArrayList(control.getUsages(loopBody));
            for (int i = 0; i < usages.size(); i++) {
                control.replaceEdge(usages.getInt(i), loopBody, headerNode);
            }

            // The original destination of the exit block is now reachable
            // from the guard and from the new exit block through the new merge
            final int iX = control.getOperands(exitBranchEnd).indexOf(exitBranchNode);
            control.removeEdge(exitBranchNode, exitBranchEnd);
            control.addEdgePair(NewMerge, 0, exitBranchEnd, iX);
        }

        private void applyRepairs() {
            for (int i = 0; i < repairN.size(); i++) {
                final boolean mem = repairMem.getInt(i) != 0;
                final var edge = mem ? memory : value;
                final int phi = (mem ? phiM : phiV).get(repairN.getInt(i));
                edge.replaceEdge(repairU.getInt(i), repairN.getInt(i), phi);
            }
        }

        private void enqueueTouched() {
            for (int i = 0; i < created.size(); i++) {
                enqueue(created.getInt(i));
            }
            for (int i = 0; i < repairU.size(); i++) {
                enqueue(repairU.getInt(i));
            }
            enqueue(headerNode);
            enqueue(headerIf);
            enqueue(exitBranchEnd);
            enqueue(loopBodyEnd);
            enqueue(exitBranchNode);
            enqueue(loopBody);
            enqueue(cond);
            enqueue(guardCond);
            enqueue(latchCond);
            enqueue(parentBegin);
            enqueue(latchBegin);
        }
    }
}