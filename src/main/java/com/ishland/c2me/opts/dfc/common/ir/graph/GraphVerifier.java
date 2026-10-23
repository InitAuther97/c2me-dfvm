package com.ishland.c2me.opts.dfc.common.ir.graph;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.LongUtil;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelBeginNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelDescriptorNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.BeginBlockNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.BranchBeginNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ControlNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.EndBlockNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.FunctionBeginNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.function.FunctionNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.IfBranchNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.MergeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ProgramNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ReturnNode;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.RootNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.PhaseStage;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.BitSet;

/// Verifies the structural invariants of the graph for a given
/// {@link PhaseStage}.
///
/// All stages share the boundary rules: the graph holds exactly one
/// {@link ProgramNode#START} and one {@link ProgramNode#END}, START has no
/// control inputs and END has no control outputs.
///
/// - {@link PhaseStage#TREE}: START points to one or more {@link RootNode}s;
///   every RootNode takes its only control edge from START and is consumed
///   by END. No programs are defined and no other control nodes exist.
/// - {@link PhaseStage#HIR}: START fans out to at least one
///   {@link KernelBeginNode}, and every kernel flows into exactly one
///   {@link ReturnNode} collected by END.
/// - {@link PhaseStage#MIR}: the same program structure with
///   {@link FunctionBeginNode} entries, additionally validating the block
///   structure ({@link IfBranchNode}, {@link BranchBeginNode},
///   {@link MergeNode}, {@link ReturnNode}).
///
/// Value and memory edges, as well as scheduling edges from control nodes
/// to non-control nodes, carry no requirements and are not checked.
public final class GraphVerifier {

    private GraphVerifier() {
    }

    /// Checks all invariants of the given stage, throwing {@link AssertionError}
    /// on the first violation found.
    public static void verify(GraphContext ctx, PhaseStage stage) {
        final IrGraph graph = ctx.getIrGraph();
        final int start = ctx.getProgramStart();
        final int end = ctx.getProgramEnd();
        verifyBoundaries(graph, start, end);
        switch (stage) {
            case TREE -> verifyTree(graph, start, end);
            case HIR -> verifyPrograms(graph, start, end, true);
            case MIR -> verifyPrograms(graph, start, end, false);
        }
    }

    private static void verifyBoundaries(IrGraph graph, int start, int end) {
        check(graph.get(start) != null && graph.getNode(start) == ProgramNode.START,
                "Program start %d is not ProgramNode.START", start);
        check(graph.get(end) != null && graph.getNode(end) == ProgramNode.END,
                "Program end %d is not ProgramNode.END", end);
        check(graph.getControl().getOperands(start).isEmpty(),
                "Program start %d must not have control inputs", start);
        check(graph.getControl().getUsages(end).isEmpty(),
                "Program end %d must not have control outputs", end);
    }

    /// Tree stage: every RootNode consumes START and is consumed by END; no
    /// other control-spine nodes and no defined programs.
    private static void verifyTree(IrGraph graph, int start, int end) {
        final EdgeAccessor control = graph.getControl();
        final IntList entries = control.getUsages(start);
        check(!entries.isEmpty(),
                "Tree stage program start %d must point to at least one RootNode", start);
        for (int i = 0; i < entries.size(); i++) {
            final int root = entries.getInt(i);
            check(graph.getNode(root) instanceof RootNode,
                    "Tree stage program start %d may only point to RootNodes, got %s at %d",
                    start, name(graph, root), root);
        }
        final IntList endOperands = control.getOperands(end);
        for (int i = 0; i < endOperands.size(); i++) {
            final int operand = endOperands.getInt(i);
            check(graph.getNode(operand) instanceof RootNode,
                    "Program end %d may only be pointed to by RootNodes in the tree stage, got %s at %d",
                    end, name(graph, operand), operand);
        }
        check(graph.countPrograms() == 0,
                "Tree stage must not define programs, got %d", graph.countPrograms());
        for (int id = 0; id < graph.nodes().size(); id++) {
            if (graph.get(id) == null) continue;
            final IrNode node = graph.getNode(id);
            if (node instanceof ProgramNode boundary) {
                check((id == start) == (boundary == ProgramNode.START),
                        "Duplicate program boundary node at %d", id);
            } else if (node instanceof RootNode) {
                final IntList operands = control.getOperands(id);
                check(operands.size() == 1 && operands.getInt(0) == start,
                        "RootNode %d must have START %d as its only control operand, got %s",
                        id, start, operands);
                int spineSuccessors = 0;
                final IntList usages = control.getUsages(id);
                for (int i = 0; i < usages.size(); i++) {
                    final int usage = usages.getInt(i);
                    final IrNode usageNode = graph.getNode(usage);
                    if (usageNode instanceof ControlNode || usageNode instanceof RootNode) {
                        check(usage == end,
                                "RootNode %d may only point to END %d on the control spine, got %s at %d",
                                id, end, name(graph, usage), usage);
                        spineSuccessors++;
                    }
                }
                check(spineSuccessors == 1,
                        "RootNode %d must flow into END %d, got %s", id, end, usages);
            } else {
                check(!(node instanceof ControlNode),
                        "Tree stage may not contain control node %s at %d", name(graph, id), id);
            }
        }
    }

    private static void verifyPrograms(IrGraph graph, int start, int end, boolean hir) {
        final EdgeAccessor control = graph.getControl();
        final String entryKind = hir ? "KernelBeginNode" : "FunctionBeginNode";
        final String stageName = hir ? "HIR" : "MIR";

        final IntList entries = control.getUsages(start);
        check(!entries.isEmpty(),
                "Program start %d must point to at least one %s entry", start, entryKind);
        for (int i = 0; i < entries.size(); i++) {
            final int entry = entries.getInt(i);
            check(hir
                            ? graph.getNode(entry) instanceof KernelBeginNode
                            : graph.getNode(entry) instanceof FunctionBeginNode,
                    "Program start %d may only point to %s entries in the %s stage, got %s at %d",
                    start, entryKind, stageName, name(graph, entry), entry);
        }

        // END collects ReturnNodes only
        final IntList returns = control.getOperands(end);
        for (int i = 0; i < returns.size(); i++) {
            final int ret = returns.getInt(i);
            check(graph.getNode(ret) == ReturnNode.RET,
                    "Program end %d may only be pointed to by ReturnNode, got %s at %d",
                    end, name(graph, ret), ret);
        }

        // The program table mirrors the entry/exit pairs of the control edges
        check(graph.countPrograms() == entries.size(),
                "Defined program count %d does not match %d %s entries under START",
                graph.countPrograms(), entries.size(), entryKind);
        for (int i = 0; i < graph.countPrograms(); i++) {
            final long program = graph.getProgramAt(i);
            final int begin = LongUtil.getLow(program);
            final int ret = LongUtil.getHigh(program);
            check(entries.contains(begin),
                    "Defined program entry %d is not connected to START %d", begin, start);
            check(returns.contains(ret),
                    "Defined program exit %d is not connected to END %d", ret, end);
        }

        // Per-node structural rules
        for (int id = 0; id < graph.nodes().size(); id++) {
            if (graph.get(id) == null) continue;
            switch (graph.getNode(id)) {
                case ProgramNode boundary -> check((id == start) == (boundary == ProgramNode.START),
                        "Duplicate program boundary node at %d", id);
                case FunctionBeginNode ignored -> verifyEntry(graph, id, start);
                case KernelBeginNode ignored -> verifyEntry(graph, id, start);
                case KernelDescriptorNode descriptor -> verifyDescriptor(graph, id, descriptor);
                case IfBranchNode ignored -> verifyIfBranch(graph, id);
                case BranchBeginNode branch -> verifyBranchBegin(graph, id, branch);
                case MergeNode ignored -> verifyMerge(graph, id);
                case ReturnNode ignored -> verifyReturn(graph, id, end);
                default -> { // non-control node: no requirements for now
                }
            }
        }

        // Per-program flow: each entry reaches exactly one return, and every
        // node it reaches flows into that return
        final BitSet visited = new BitSet();
        for (int i = 0; i < graph.countPrograms(); i++) {
            final long program = graph.getProgramAt(i);
            verifyProgramFlow(graph, LongUtil.getLow(program), LongUtil.getHigh(program), visited);
        }

        // Every control node belongs to a program
        for (int id = 0; id < graph.nodes().size(); id++) {
            if (graph.get(id) == null) continue;
            final IrNode node = graph.getNode(id);
            if (node instanceof ControlNode && !(node instanceof ProgramNode)) {
                check(visited.get(id),
                        "Control node %d (%s) is not reachable from any program entry", id, name(graph, id));
            }
        }
    }

    private static void verifyEntry(IrGraph graph, int id, int start) {
        final IntList operands = graph.getControl().getOperands(id);
        check(operands.size() == 1 && operands.getInt(0) == start,
                "Program entry %d must have START %d as its only control operand, got %s", id, start, operands);
        verifyBlockEnd(graph, id);
    }

    /// A foreign kernel has no implementation in the graph, so its descriptor
    /// must not be linked to a kernel entry.
    private static void verifyDescriptor(IrGraph graph, int id, KernelDescriptorNode descriptor) {
        if (!descriptor.isForeign()) {
            return;
        }
        final IntList usages = graph.getMemory().getUsages(id);
        for (int i = 0; i < usages.size(); i++) {
            final int usage = usages.getInt(i);
            check(!(graph.getNode(usage) instanceof KernelBeginNode),
                    "Foreign kernel descriptor %d (%s) must not be linked to KernelBeginNode %d",
                    id, descriptor.name(), usage);
        }
    }

    private static void verifyIfBranch(IrGraph graph, int id) {
        final EdgeAccessor control = graph.getControl();
        final IntList operands = control.getOperands(id);
        check(operands.size() == 1 && graph.getNode(operands.getInt(0)) instanceof BeginBlockNode,
                "IfBranchNode %d must have exactly one BeginBlockNode control operand, got %s", id, operands);
        final IntList usages = control.getUsages(id);
        check(usages.size() == 2,
                "IfBranchNode %d must have exactly two BranchBeginNode successors, got %s", id, usages);
        check(graph.getNode(usages.getInt(0)) == BranchBeginNode.TRUE
                        && graph.getNode(usages.getInt(1)) == BranchBeginNode.FALSE,
                "IfBranchNode %d successors must be BranchBeginNode.TRUE then BranchBeginNode.FALSE, got %s",
                id, usages);
    }

    private static void verifyBranchBegin(IrGraph graph, int id, BranchBeginNode branch) {
        final IntList operands = graph.getControl().getOperands(id);
        check(operands.size() == 1 && graph.getNode(operands.getInt(0)) instanceof IfBranchNode,
                "BranchBeginNode %d must have exactly one IfBranchNode control operand, got %s", id, operands);
        // The slot under the IfBranchNode is fixed: TRUE at 0, FALSE at 1
        final IntList usages = graph.getControl().getUsages(operands.getInt(0));
        check(usages.size() == 2 && graph.getNode(usages.getInt(branch == BranchBeginNode.TRUE ? 0 : 1)) == branch,
                "BranchBeginNode %d is not at its required successor slot", id);
        verifyBlockEnd(graph, id);
    }

    private static void verifyMerge(IrGraph graph, int id) {
        final IntList operands = graph.getControl().getOperands(id);
        check(operands.size() == 2
                        && graph.getNode(operands.getInt(0)) instanceof BeginBlockNode
                        && graph.getNode(operands.getInt(1)) instanceof BeginBlockNode,
                "MergeNode %d must have exactly two BeginBlockNode control operands, got %s", id, operands);
        verifyBlockEnd(graph, id);
    }

    private static void verifyReturn(IrGraph graph, int id, int end) {
        final EdgeAccessor control = graph.getControl();
        final IntList operands = control.getOperands(id);
        check(operands.size() == 1 && graph.getNode(operands.getInt(0)) instanceof BeginBlockNode,
                "ReturnNode %d must have exactly one BeginBlockNode control operand, got %s", id, operands);
        final IntList usages = control.getUsages(id);
        check(usages.size() == 1 && usages.getInt(0) == end,
                "ReturnNode %d must flow into END %d, got %s", id, end, usages);
    }

    /// Every block begin has exactly one control edge to the EndBlockNode
    /// terminating its block; other usages may only be scheduling edges to
    /// non-control nodes.
    private static void verifyBlockEnd(IrGraph graph, int id) {
        final IntList usages = graph.getControl().getUsages(id);
        int blockEnd = -1;
        for (int i = 0; i < usages.size(); i++) {
            final int usage = usages.getInt(i);
            if (graph.get(usage) == null || !(graph.getNode(usage) instanceof ControlNode)) continue;
            check(blockEnd == -1,
                    "BeginBlockNode %d has multiple control successors: %d and %d", id, blockEnd, usage);
            blockEnd = usage;
        }
        check(blockEnd != -1,
                "BeginBlockNode %d has no control edge to its EndBlockNode", id);
        check(graph.getNode(blockEnd) instanceof EndBlockNode,
                "BeginBlockNode %d block end %d (%s) is not an EndBlockNode",
                id, blockEnd, name(graph, blockEnd));
    }

    /// All control flow from {@code begin} must terminate at exactly the
    /// ReturnNode {@code ret}, and no control node may be shared between
    /// programs.
    private static void verifyProgramFlow(IrGraph graph, int begin, int ret, BitSet visited) {
        final BitSet forward = walk(graph, begin, false);
        final BitSet reachesReturn = walk(graph, ret, true);
        check(forward.get(ret),
                "ReturnNode %d is not reachable from program entry %d", ret, begin);
        for (int id = forward.nextSetBit(0); id >= 0; id = forward.nextSetBit(id + 1)) {
            check(reachesReturn.get(id),
                    "Control node %d (%s) does not eventually flow into ReturnNode %d",
                    id, name(graph, id), ret);
            check(id == ret || graph.getNode(id) != ReturnNode.RET,
                    "Program entry %d reaches another ReturnNode %d besides %d", begin, id, ret);
            check(!visited.get(id),
                    "Control node %d (%s) is shared between programs", id, name(graph, id));
        }
        visited.or(forward);
    }

    /// Walks control-to-control edges from {@code root}, stopping at the
    /// program boundaries; scheduling edges to non-control nodes are ignored.
    private static BitSet walk(IrGraph graph, int root, boolean backward) {
        final EdgeAccessor control = graph.getControl();
        final BitSet visited = new BitSet();
        final IntArrayList stack = new IntArrayList();
        visited.set(root);
        stack.push(root);
        while (!stack.isEmpty()) {
            final IntList next = backward ? control.getOperands(stack.popInt()) : control.getUsages(stack.popInt());
            for (int i = 0; i < next.size(); i++) {
                final int id = next.getInt(i);
                if (graph.get(id) == null || !(graph.getNode(id) instanceof ControlNode)
                        || graph.getNode(id) instanceof ProgramNode) continue;
                if (visited.get(id)) continue;
                visited.set(id);
                stack.push(id);
            }
        }
        return visited;
    }

    private static String name(IrGraph graph, int id) {
        return switch (graph.getNode(id)) {
            case FunctionBeginNode ignored -> "FunctionBeginNode(%s)".formatted(functionName(graph, id));
            case KernelBeginNode ignored -> "KernelBeginNode(%s)".formatted(functionName(graph, id));
            case IrNode node -> node.getClass().getSimpleName();
            case null -> "null";
        };
    }

    /// The name of the function marked by a begin node's memory operand, or
    /// "?" if the link is missing.
    private static String functionName(IrGraph graph, int id) {
        final var operands = graph.getMemory().getOperands(id);
        for (int i = 0; i < operands.size(); i++) {
            final int operand = operands.getInt(i);
            final var state = graph.get(operand);
            if (state != null && state.node() instanceof FunctionNode function) {
                return function.name();
            }
            if (state != null && state.node() instanceof KernelDescriptorNode descriptor) {
                return descriptor.name();
            }
        }
        return "?";
    }

    private static void check(boolean condition, String format, Object... args) {
        if (!condition) {
            throw new AssertionError(String.format(format, args));
        }
    }
}