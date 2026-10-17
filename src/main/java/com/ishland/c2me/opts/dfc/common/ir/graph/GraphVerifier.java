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
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.FunctionNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.IfBranchNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.MergeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ProgramNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ReturnNode;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.BitSet;

/// Verifies the structural invariants of the control spine:
///
/// - A graph holds exactly one {@link ProgramNode#START} and one
///   {@link ProgramNode#END}; START fans out to at least one
///   {@link FunctionBeginNode} and every {@link ReturnNode} flows into END.
/// - Every {@link BeginBlockNode} has exactly one control edge to the
///   {@link EndBlockNode} terminating its block.
/// - An {@link IfBranchNode} has exactly two {@link BranchBeginNode}
///   successors (TRUE at slot 0, FALSE at slot 1); a {@link MergeNode} has
///   exactly two {@link BeginBlockNode} predecessors.
/// - Every function's control flow starting at its FunctionBeginNode eventually
///   flows into exactly one ReturnNode, and the program table of
///   {@link IrGraph#defineProgram} mirrors these entry/exit pairs.
///
/// Value and memory edges, as well as scheduling edges from control nodes
/// to non-control nodes, carry no requirements and are not checked.
public final class GraphVerifier {

    private GraphVerifier() {
    }

    /// Checks all invariants, throwing {@link AssertionError} on the first
    /// violation found.
    public static void verify(GraphContext ctx) {
        final IrGraph graph = ctx.getIrGraph();
        final EdgeAccessor control = graph.getControl();
        final int start = ctx.getProgramStart();
        final int end = ctx.getProgramEnd();

        check(graph.get(start) != null && graph.getNode(start) == ProgramNode.START,
                "Program start %d is not ProgramNode.START", start);
        check(graph.get(end) != null && graph.getNode(end) == ProgramNode.END,
                "Program end %d is not ProgramNode.END", end);

        // START has no control input and fans out to function begin nodes only
        check(control.getOperands(start).isEmpty(),
                "Program start %d must not have control inputs", start);
        final IntList entries = control.getUsages(start);
        check(!entries.isEmpty(),
                "Program start %d must point to at least one function begin node", start);
        for (int i = 0; i < entries.size(); i++) {
            final int entry = entries.getInt(i);
            check(graph.getNode(entry) instanceof FunctionBeginNode || graph.getNode(entry) instanceof KernelBeginNode,
                    "Program start %d may only point to function begin nodes, got %s at %d",
                    start, name(graph, entry), entry);
        }

        // END has no control output and collects ReturnNodes only
        check(control.getUsages(end).isEmpty(),
                "Program end %d must not have control outputs", end);
        final IntList returns = control.getOperands(end);
        for (int i = 0; i < returns.size(); i++) {
            final int ret = returns.getInt(i);
            check(graph.getNode(ret) == ReturnNode.RET,
                    "Program end %d may only be pointed to by ReturnNode, got %s at %d",
                    end, name(graph, ret), ret);
        }

        // The program table mirrors the entry/exit pairs of the control edges
        check(graph.countPrograms() == entries.size(),
                "Defined program count %d does not match %d FunctionBeginNodes under START",
                graph.countPrograms(), entries.size());
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
                        "Control node %d (%s) is not reachable from any FunctionBeginNode", id, name(graph, id));
            }
        }
    }

    private static void verifyEntry(IrGraph graph, int id, int start) {
        final IntList operands = graph.getControl().getOperands(id);
        check(operands.size() == 1 && operands.getInt(0) == start,
                "FunctionBeginNode %d must have START %d as its only control operand, got %s", id, start, operands);
        verifyBlockEnd(graph, id);
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
                "ReturnNode %d is not reachable from FunctionBeginNode %d", ret, begin);
        for (int id = forward.nextSetBit(0); id >= 0; id = forward.nextSetBit(id + 1)) {
            check(reachesReturn.get(id),
                    "Control node %d (%s) does not eventually flow into ReturnNode %d",
                    id, name(graph, id), ret);
            check(id == ret || graph.getNode(id) != ReturnNode.RET,
                    "FunctionBeginNode %d reaches another ReturnNode %d besides %d", begin, id, ret);
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