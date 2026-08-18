package com.ishland.c2me.opts.dfc.common.ir.phase.cfg;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeAccessor;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.graph.cfg.CFGAccess;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.BranchBeginNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ControlNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.IfBranchNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.MergeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.memory.MemPhiNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.branch.PhiNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.RunCanonicalizerPhase;
import com.ishland.flowsched.util.Assertions;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;

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
            }
        }
    }

    private boolean rotateFirstQualified(IrGraph graph, int programIdx) {
        // We compute LoopBody here. This provides us with all blocks we need to inspect.
        final var cfg = graph.getOrComputeCFGAt(programIdx, CFGAccess.Status.LOOP_BODY);
        final var flowGraph = cfg.getCFG();
        final var control = graph.getControl();
        final var iter = cfg.loopHeaderIterator();
        while (iter.hasNext()) {
            final int headerIdx = iter.nextInt();
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
            final int exitBlock = firstExits ? firstD : secondD;
            final int backEdge = loop.latch();
            final int beginNode = cfg.getBlockBegin(backEdge);
            // Back edge block is empty, disqualify
            if (control.getUsages(beginNode).size() == 1) continue;
            if (new Rotation(graph, cfg, headerIdx, exitBlock).run()) return true;
        }
        return false;
    }

    /// Thrown when the condition slice cannot be duplicated safely, e.g.
    /// when it contains a phi pinned at another merge inside the loop.
    private static final class Bailout extends RuntimeException {
        private Bailout() {}
    }

    private final class Rotation {

        private final IrGraph graph;
        private final CFGAccess.LoopBody cfg;
        private final EdgeAccessor control, value, memory;

        private final int exitIdx;
        private final int H, IfOld, cond, Lbegin, BTexit, Xend, BTbody, XbodyEnd, Pbegin;
        private final int iP, iL, latchDest;
        private final boolean exitOnTrue, special;

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

        Rotation(IrGraph graph, CFGAccess.LoopBody cfg, int headerIdx, int exitIdx) {
            this.graph = graph;
            this.cfg = cfg;
            this.control = graph.getControl();
            this.value = graph.getValue();
            this.memory = graph.getMemory();
            this.exitIdx = exitIdx;
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
            this.H = cfg.getBlockBegin(headerIdx);
            this.IfOld = cfg.getBlockEnd(headerIdx);
            this.cond = value.getOperands(IfOld).getInt(0);
            final var loop = cfg.getLoopFromHeader(headerIdx);
            this.Lbegin = cfg.getBlockBegin(loop.latch());
            this.BTexit = cfg.getBlockBegin(exitIdx);
            this.Xend = cfg.getBlockEnd(exitIdx);
            final var ifUsages = control.getUsages(IfOld);
            // Usages of an if are ordered: TRUE branch first, then FALSE
            this.exitOnTrue = ifUsages.getInt(0) == BTexit;
            this.BTbody = ifUsages.getInt(exitOnTrue ? 1 : 0);
            this.XbodyEnd = control.getUsages(BTbody).getInt(0);
            final var hOps = control.getOperands(H);
            this.iL = hOps.indexOf(Lbegin);
            this.iP = iL < 0 ? -1 : 1 - iL;
            this.Pbegin = iP < 0 ? -1 : hOps.getInt(iP);
            // An empty body: the header's body edge is itself the back edge.
            // The latch block then merges into the header block instead.
            this.special = BTbody == Lbegin;
            this.latchDest = special ? H : Lbegin;
        }

        boolean run() {
            if (iL < 0) return false;
            try {
                if (special) reanchorLatchScheduling();
                computeTaint();
                NewMerge = graph.addNode(MergeNode.MERGE);
                created.add(NewMerge);
                // Duplicate condition nodes used, translating the phi to the
                // input of the corresponding edge
                guardCond = translate(cond, false);
                latchCond = translate(cond, true);
                scanRepairs();
                createRepairPhis();
                // No bailout may happen beyond this point
                rewire();
                applyRepairs();
            } catch (Bailout b) {
                for (int i = 0; i < created.size(); i++) {
                    enqueue(created.getInt(i));
                }
                return false;
            }
            enqueueTouched();
            return true;
        }

        /// In the empty-body case the latch block disappears; its scheduled
        /// nodes move into the header block.
        private void reanchorLatchScheduling() {
            final var usages = new IntArrayList(control.getUsages(Lbegin));
            for (int i = 0; i < usages.size(); i++) {
                final int u = usages.getInt(i);
                if (u != H) control.replaceEdge(u, Lbegin, H);
            }
        }

        /// Nodes whose computation transitively depends on a phi pinned at
        /// the loop header. Such nodes evaluate to a different value on each
        /// iteration, so the guard and the latch check each need their own
        /// copy with the phi translated; untainted nodes are shared.
        private void computeTaint() {
            final var stack = new IntArrayList();
            final var hUsages = control.getUsages(H);
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
        private int translate(int v, boolean latchDir) {
            final var memo = latchDir ? latchMemo : guardMemo;
            final int cached = memo.get(v);
            if (cached != -1) return cached;
            final var node = graph.getNode(v);
            if (node == null) throw new Bailout();
            final int dest = latchDir ? latchDest : Pbegin;
            if (tainted.get(v)) {
                if (node instanceof PhiNode || node instanceof MemPhiNode) {
                    final var cOps = control.getOperands(v);
                    if (!cOps.isEmpty() && cOps.getInt(0) == H) {
                        final var edge = node instanceof MemPhiNode ? memory : value;
                        final int result = edge.getOperands(v).getInt(latchDir ? iL : iP);
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
            final var hUsages = control.getUsages(H);
            for (int i = 0; i < hUsages.size(); i++) {
                final int n = hUsages.getInt(i);
                if (n == IfOld || graph.getNode(n) instanceof ControlNode) continue;
                collectRepairs(n, false);
                collectRepairs(n, true);
            }
            final var xUsages = control.getUsages(BTexit);
            for (int i = 0; i < xUsages.size(); i++) {
                final int n = xUsages.getInt(i);
                if (n == Xend) continue;
                collectRepairs(n, false);
                collectRepairs(n, true);
            }
        }

        private void collectRepairs(int n, boolean mem) {
            final var edge = mem ? memory : value;
            final var usages = edge.getUsages(n);
            for (int i = 0; i < usages.size(); i++) {
                final int u = usages.getInt(i);
                if (u == IfOld) continue;
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
                    if (u == Xend) {
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

        private void createRepairPhis() {
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
            control.replaceEdge(H, Pbegin, gbody);
            control.addEdgePair(Pbegin, 0, guardIf, 0);
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
            if (!special) {
                control.removeEdge(H, IfOld);
                control.addEdgePair(Lbegin, 0, IfOld, 0);
            }
            value.replaceEdge(IfOld, cond, latchCond);
            control.removeEdge(IfOld, BTexit);
            control.removeEdge(IfOld, BTbody);
            if (exitOnTrue) {
                control.appendEdge(IfOld, lexit);
                control.appendEdge(IfOld, lback);
            } else {
                control.appendEdge(IfOld, lback);
                control.appendEdge(IfOld, lexit);
            }
            control.replaceEdge(H, Lbegin, lback);
            control.appendEdge(lexit, NewMerge);

            // The header block now falls through into the body
            if (!special) {
                final int iB = control.getOperands(XbodyEnd).indexOf(BTbody);
                control.removeEdge(BTbody, XbodyEnd);
                control.addEdgePair(H, 0, XbodyEnd, iB);
                final var usages = new IntArrayList(control.getUsages(BTbody));
                for (int i = 0; i < usages.size(); i++) {
                    control.replaceEdge(usages.getInt(i), BTbody, H);
                }
            }

            // The original destination of the exit block is now reachable
            // from the guard and from the new exit block through the new merge
            final int iX = control.getOperands(Xend).indexOf(BTexit);
            control.removeEdge(BTexit, Xend);
            control.addEdgePair(NewMerge, 0, Xend, iX);
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
            enqueue(H);
            enqueue(IfOld);
            enqueue(Xend);
            enqueue(XbodyEnd);
            enqueue(BTexit);
            enqueue(BTbody);
            enqueue(cond);
            enqueue(guardCond);
            enqueue(latchCond);
            enqueue(Pbegin);
            enqueue(Lbegin);
        }
    }
}