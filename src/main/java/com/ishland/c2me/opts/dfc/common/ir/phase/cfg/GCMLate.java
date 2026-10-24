package com.ishland.c2me.opts.dfc.common.ir.phase.cfg;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.LongUtil;
import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeAccessor;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.graph.cfg.CFGAccess;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.function.FunctionArgumentNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.function.FunctionNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.MergeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ReturnNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.memory.MemPhiNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.branch.PhiNode;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.MemoryType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.TypeUtil;
import com.ishland.flowsched.util.Assertions;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import java.util.BitSet;

// Phi is not touched in this stage because Phi is already pinned at Merge, which is an invariant.
public class GCMLate implements SchedulePhase {

    private static final ThreadLocal<Workspace> WORKSPACE_CACHE = ThreadLocal.withInitial(Workspace::new);

    record Workspace(IntArrayList list, BitSet bitset) {
        Workspace() {
            this(new IntArrayList(), new BitSet());
        }
    }

    private final GraphContext context;
    private final IntArrayList stack;
    private final BitSet availableNodes;

    public GCMLate(GraphContext ctx) {
        this.context = ctx;
        final var workspace = WORKSPACE_CACHE.get();
        this.stack = workspace.list;
        this.availableNodes = workspace.bitset;
    }

    class ScopeBoundScheduler implements EdgeAccessor.DepthFirstVisitor {

        private final GraphContext context;
        private final CFGAccess.Dominator cfg;
        private final ReferenceArrayList<BitSet> byDepth = new ReferenceArrayList<>();
        private int depth = 0;

        ScopeBoundScheduler(GraphContext context, CFGAccess.Dominator cfg) {
            this.context = context;
            this.cfg = cfg;
        }

        @Override
        public boolean enter(int bbIdx) {
            final BitSet bitset;
            final int depth = this.depth++;
            if (depth == byDepth.size()) {
                bitset = new BitSet();
                byDepth.add(bitset);
            } else {
                bitset = byDepth.get(depth);
            }
            schedule(context, bitset, cfg.getBasicBlock(bbIdx));
            return true;
        }

        @Override
        public void leave(int node) {
            final var bitset = byDepth.get(--depth);
            availableNodes.andNot(bitset);
            final var ctrl = context.getIrGraph().getControl();
            final int begin = cfg.getBlockBegin(node);
            int i = -1;
            while ((i = bitset.nextSetBit(i + 1)) != -1) {
                ctrl.appendEdge(begin, i);
            }
            bitset.clear();
        }
    }

    /// Assign all nodes to its latest used location.
    /// Phi nodes are assigned to its respective Merge node.
    /// Input of Phi nodes are assigned to its respective branch begin node.
    /// Returns have zero or one value result and at most one memory result per
    /// memory input alias declared by the corresponding function.
    @Override
    public void run() {
        final var graph = context.getIrGraph();
        for (int i = 0; i < graph.countPrograms(); i++) {
            final long program = graph.getProgramAt(i);
            verifyReturnOperands(graph, LongUtil.getLow(program), LongUtil.getHigh(program));
        }
        for (int i = 0; i < graph.countPrograms(); i++) {
            final var cfg = graph.getOrComputeCFGAt(i, CFGAccess.Status.DOMINATOR);
            final var visitor = new ScopeBoundScheduler(context, cfg);
            cfg.getDomTree().walk(visitor);
        }
    }

    private static void verifyReturnOperands(IrGraph graph, int entry, int ret) {
        final var values = graph.getValue().getOperands(ret);
        Assertions.assertTrue(values.size() <= 1,
                "ReturnNode %d must have at most one return value, got %s", ret, values);
        final var memory = graph.getMemory();
        final var memories = memory.getOperands(ret);
        if (memories.isEmpty()) return;

        final var entryMemory = memory.getOperands(entry);
        Assertions.assertTrue(!entryMemory.isEmpty()
                        && graph.getNode(entryMemory.getInt(0)) instanceof FunctionNode,
                "Function entry %d must identify its FunctionNode through memory operand 0", entry);
        final int descriptor = entryMemory.getInt(0);
        final IntSet inputAliases = new IntOpenHashSet();
        for (int argument : memory.getUsages(descriptor)) {
            if (!(graph.getNode(argument) instanceof FunctionArgumentNode)) continue;
            if (TypeUtil.getMemoryType(graph, argument) instanceof MemoryType type) {
                Assertions.assertTrue(type.getAlias() >= 0,
                        "Memory input argument %d must have a concrete alias", argument);
                inputAliases.add(type.getAlias());
            }
        }

        final IntSet returnedAliases = new IntOpenHashSet();
        for (int returned : memories) {
            final var type = TypeUtil.getMemoryType(graph, returned);
            Assertions.assertTrue(type instanceof MemoryType && ((MemoryType) type).getAlias() >= 0,
                    "ReturnNode %d memory operand %d must have a concrete memory alias", ret, returned);
            final int alias = ((MemoryType) type).getAlias();
            Assertions.assertTrue(inputAliases.contains(alias),
                    "ReturnNode %d memory alias %d must correspond to a memory input argument of function %d",
                    ret, alias, descriptor);
            Assertions.assertTrue(returnedAliases.add(alias),
                    "ReturnNode %d must have at most one memory return for input alias %d", ret, alias);
        }
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
                Assertions.assertTrue(phiOrMemPhi == PhiNode.PHI || phiOrMemPhi == MemPhiNode.PHI,
                        "Expected only PhiNode or MemPhiNode scheduled before global code motion");
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
        if (!values.isEmpty()) {
            schedLateIterative(graph, scheduling, values.getInt(0));
        }
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
