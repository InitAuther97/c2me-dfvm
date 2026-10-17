package com.ishland.c2me.opts.dfc.common.ir.graph.cfg;

import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.PathSelectionNode;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;

public class InsnView {

    private static final ThreadLocal<Workspace> WORKSPACE_CACHE = ThreadLocal.withInitial(Workspace::new);

    record Workspace(IntArrayList instances, IntArrayList dfs) {
        Workspace() {
            this(new IntArrayList(), new IntArrayList());
        }
    }

    public static void rewriteDominatedUses(IrGraph graph, Int2IntMap valueMap, DomTree dom, int bbIdx) {
        final var ctrl = graph.getControl();
        final var mem = graph.getMemory();
        final var val = graph.getValue();
        final var workspace = WORKSPACE_CACHE.get();
        final var collected = workspace.instances;
        final var stack = workspace.dfs;
        final var rewrite = new Int2IntArrayMap(valueMap);
        stack.addAll(valueMap.keySet());
        int current;
        while (!stack.isEmpty()) {
            current = stack.popInt();
            final var node = graph.getNode(current);
            if (PathSelectionNode.isBranchSelection(node)) {
                // FIXME: There is no
            }
            final var insns = graph.getControl().getOperands(current);
            collected.ensureCapacity(insns.size());
            for (int i = 0; i < insns.size(); i++) {
                final int insn = insns.getInt(i);
                if (dom.isDominatedBy(insn, bbIdx))
                    collected.add(insn);
            }
            final var mOps = mem.getOperands(current);
            final var vOps = val.getOperands(current);
            if (insns.size() == collected.size()) {
                // Fast path: all instances are to be rewritten
                // This means all usages of the instances are to be rewritten
                // This propagates on, and we have nothing to exclude
                mem.bulkReplaceSrc(current, rewrite);
                val.bulkReplaceSrc(current, rewrite);
            } else {
                int nodeToReplace = graph.addNode(graph.getNode(current));
                for (int i = 0; i < collected.size(); i++) {
                    final int sched = collected.getInt(i);
                    ctrl.appendEdge(sched, nodeToReplace);
                    ctrl.removeEdge(sched, current);
                }
                for (int i = 0; i < mOps.size(); i++) {
                    final int mOp = mOps.getInt(i);
                    final int updated = rewrite.getOrDefault(mOp, mOp);
                    mem.appendEdge(updated, nodeToReplace);
                }
                for (int i = 0; i < vOps.size(); i++) {
                    final int vOp = vOps.getInt(i);
                    final int updated = rewrite.getOrDefault(vOp, vOp);
                    val.appendEdge(updated, nodeToReplace);
                }
                rewrite.put(current, nodeToReplace);
                stack.addAll(mem.getUsages(current));
                stack.addAll(val.getUsages(current));
            }
            collected.clear();
        }
    }
}
