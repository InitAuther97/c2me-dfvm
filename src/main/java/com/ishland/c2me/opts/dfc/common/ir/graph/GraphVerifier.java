package com.ishland.c2me.opts.dfc.common.ir.graph;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.LongUtil;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelArgumentNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelRootNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.PathSelectionNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.ProxyNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.memory.MemOpNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.BeginBlockNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.BranchBeginNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ConstantPoolNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ControlNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.EndBlockNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.FunctionBeginNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.function.FunctionNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.function.FunctionArgumentNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.IfBranchNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.MergeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ProgramNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ReturnNode;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.RootNode;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.TreeIRNode;
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
/// - {@link PhaseStage#HIR}: START points to body roots, each consuming its
///   kernel descriptor and one result value. Bodies are pure value DAGs;
///   argument memory operands mark ownership. No MIR programs are defined.
/// - {@link PhaseStage#MIR}: executable programs with
///   {@link FunctionBeginNode} entries and explicit block
///   structure ({@link IfBranchNode}, {@link BranchBeginNode},
///   {@link MergeNode}, {@link ReturnNode}).
///
/// HIR checks value/memory acyclicity and kernel ownership. MIR value and
/// memory edges and scheduling edges are not structurally checked.
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
        verifyConstantPool(graph, start, end, ctx.getConstantPool());
        switch (stage) {
            case TREE -> verifyTree(graph, start, end);
            case HIR -> verifyHir(graph, start, end);
            case MIR -> verifyPrograms(graph, start, end);
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
        for (int id = 0; id < graph.nodes().size(); id++) {
            if (graph.get(id) != null && graph.getNode(id) instanceof ProgramNode boundary) {
                check(id == (boundary == ProgramNode.START ? start : end),
                        "Duplicate program boundary node at %d", id);
            }
        }
    }

    /// The constant pool takes control from START, flows into END and is the
    /// memory source of every pooled constant.
    private static void verifyConstantPool(IrGraph graph, int start, int end, int pool) {
        check(graph.get(pool) != null && graph.getNode(pool) instanceof ConstantPoolNode,
                "Constant pool %d is not ConstantPoolNode.POOL", pool);
        final IntList operands = graph.getControl().getOperands(pool);
        check(operands.size() == 1 && operands.getInt(0) == start,
                "ConstantPoolNode %d must have START %d as its only control operand, got %s",
                pool, start, operands);
        int spineSuccessors = 0;
        final IntList usages = graph.getControl().getUsages(pool);
        for (int i = 0; i < usages.size(); i++) {
            final int usage = usages.getInt(i);
            if (graph.getNode(usage) instanceof ControlNode) {
                check(usage == end,
                        "ConstantPoolNode %d may only point to END %d on the control spine, got %s at %d",
                        pool, end, name(graph, usage), usage);
                spineSuccessors++;
            }
        }
        check(spineSuccessors == 1,
                "ConstantPoolNode %d must flow into END %d, got %s", pool, end, usages);
        int pools = 0;
        for (int id = 0; id < graph.nodes().size(); id++) {
            if (graph.get(id) != null && graph.getNode(id) instanceof ConstantPoolNode) {
                check(id == pool, "Duplicate ConstantPoolNode at %d", id);
                pools++;
            }
        }
        check(pools == 1, "Graph must contain exactly one ConstantPoolNode, got %d", pools);
    }

    /// Tree stage: every RootNode consumes START and is consumed by END; no
    /// other control-spine nodes and no defined programs.
    private static void verifyTree(IrGraph graph, int start, int end) {
        final EdgeAccessor control = graph.getControl();
        final IntList entries = control.getUsages(start);
        int rootCount = 0;
        for (int i = 0; i < entries.size(); i++) {
            final int root = entries.getInt(i);
            final IrNode node = graph.getNode(root);
            check(node instanceof RootNode || node instanceof ConstantPoolNode,
                    "Tree stage program start %d may only point to RootNodes and the constant pool, got %s at %d",
                    start, name(graph, root), root);
            if (node instanceof RootNode) rootCount++;
        }
        check(rootCount > 0,
                "Tree stage program start %d must point to at least one RootNode", start);
        final IntList endOperands = control.getOperands(end);
        for (int i = 0; i < endOperands.size(); i++) {
            final int operand = endOperands.getInt(i);
            final IrNode node = graph.getNode(operand);
            check(node instanceof RootNode || node instanceof ConstantPoolNode,
                    "Program end %d may only be pointed to by RootNodes and the constant pool in the tree stage, got %s at %d",
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
            } else if (node instanceof ConstantPoolNode) {
                // verified by verifyConstantPool
            } else {
                check(!(node instanceof ControlNode),
                        "Tree stage may not contain control node %s at %d", name(graph, id), id);
            }
        }
    }

    private static void verifyHir(IrGraph graph, int start, int end) {
        final EdgeAccessor control = graph.getControl();
        check(graph.countPrograms() == 0,
                "HIR value DAGs must not define MIR programs, got %d", graph.countPrograms());
        int roots = 0;
        for (int id : control.getUsages(start)) {
            check(graph.getNode(id) instanceof KernelRootNode || graph.getNode(id) instanceof ConstantPoolNode,
                    "HIR START may only point to kernel roots and the constant pool, got %s at %d", name(graph, id), id);
            if (graph.getNode(id) instanceof KernelRootNode) roots++;
        }
        check(roots > 0, "HIR must contain at least one kernel body root");
        for (int id : control.getOperands(end)) {
            check(graph.getNode(id) instanceof KernelRootNode || graph.getNode(id) instanceof ConstantPoolNode,
                    "HIR END may only collect kernel roots and the constant pool, got %s at %d", name(graph, id), id);
        }

        for (int id = 0; id < graph.nodes().size(); id++) {
            if (graph.get(id) == null) continue;
            final IrNode node = graph.getNode(id);
            if (node instanceof ProgramNode || node instanceof ConstantPoolNode || node instanceof KernelNode) {
                check(graph.getValue().getUsages(id).isEmpty(),
                        "HIR structural node %d (%s) must not be consumed as a value", id, name(graph, id));
            }
            if (node instanceof ProgramNode || node instanceof ConstantPoolNode) continue;
            check(!(node instanceof ControlNode || node instanceof PathSelectionNode
                            || node instanceof ProxyNode || node instanceof MemOpNode
                            || node instanceof FunctionNode || node instanceof FunctionArgumentNode
                            || node instanceof TreeIRNode),
                    "HIR must not contain explicit control flow, memory operations, or unlowered tree nodes: %s at %d",
                    name(graph, id), id);
            if (node instanceof KernelRootNode) {
                final IntList operands = control.getOperands(id);
                final IntList usages = control.getUsages(id);
                check(operands.size() == 1 && operands.getInt(0) == start
                                && usages.size() == 1 && usages.getInt(0) == end,
                        "KernelRootNode %d must be structurally attached START -> root -> END", id);
                final int owner = kernelOwner(graph, id);
                check(!((KernelNode) graph.getNode(owner)).isForeign(),
                        "Foreign kernel %d must not own KernelRootNode %d", owner, id);
                check(graph.getValue().getOperands(id).size() == 1,
                        "KernelRootNode %d must consume exactly one result value", id);
                check(graph.getValue().getUsages(id).isEmpty() && graph.getMemory().getUsages(id).isEmpty(),
                        "KernelRootNode %d is a body anchor, not an operand", id);
            } else {
                check(control.getOperands(id).isEmpty() && control.getUsages(id).isEmpty(),
                        "HIR body node %d (%s) must not have control edges", id, name(graph, id));
            }
            if (node instanceof KernelArgumentNode) {
                final int owner = kernelOwner(graph, id);
                check(!((KernelNode) graph.getNode(owner)).isForeign(),
                        "Foreign kernel %d must not own argument %d", owner, id);
                check(graph.getValue().getOperands(id).isEmpty(),
                        "KernelArgumentNode %d must not consume value operands", id);
            } else if (node instanceof KernelNode descriptor) {
                verifyDescriptor(graph, id, descriptor);
            }
        }
        verifyHirDag(graph);
        for (int id = 0; id < graph.nodes().size(); id++) {
            if (graph.get(id) != null && graph.getNode(id) instanceof KernelRootNode) {
                verifyKernelBody(graph, id, kernelOwner(graph, id));
            }
        }
    }

    /// Roots and arguments consume exactly one descriptor marker.
    private static int kernelOwner(IrGraph graph, int id) {
        final IntList memory = graph.getMemory().getOperands(id);
        check(memory.size() == 1 && graph.getNode(memory.getInt(0)) instanceof KernelNode,
                "HIR root/argument %d must have exactly one KernelNode memory operand, got %s", id, memory);
        return memory.getInt(0);
    }

    private static void verifyDescriptor(IrGraph graph, int id, KernelNode descriptor) {
        check(graph.getValue().getOperands(id).isEmpty(),
                "KernelNode %d must not take its body as a value operand", id);
        int roots = 0;
        for (int usage : graph.getMemory().getUsages(id)) {
            if (graph.getNode(usage) instanceof KernelRootNode) roots++;
        }
        check(roots == (descriptor.isForeign() ? 0 : 1),
                "KernelNode %d (%s) must own %d body roots, got %d",
                id, descriptor.name(), descriptor.isForeign() ? 0 : 1, roots);
    }

    /// Marker and metadata dependencies must not introduce cycles either.
    private static void verifyHirDag(IrGraph graph) {
        final int[] remaining = new int[graph.nodes().size()];
        final IntArrayList ready = new IntArrayList();
        for (int id = 0; id < remaining.length; id++) {
            if (graph.get(id) == null) continue;
            remaining[id] = graph.getValue().getOperands(id).size() + graph.getMemory().getOperands(id).size();
            if (remaining[id] == 0) ready.add(id);
        }
        int processed = 0;
        while (!ready.isEmpty()) {
            final int id = ready.popInt();
            processed++;
            for (int usage : graph.getValue().getUsages(id)) {
                if (--remaining[usage] == 0) ready.add(usage);
            }
            for (int usage : graph.getMemory().getUsages(id)) {
                if (--remaining[usage] == 0) ready.add(usage);
            }
        }
        check(processed == graph.size(), "HIR value and metadata dependencies must form a DAG");
    }

    /// Follow metadata too, so coordinate expressions in packed volumes are
    /// checked against the caller's argument ownership. Callee descriptors
    /// are markers, not edges into other bodies.
    private static void verifyKernelBody(IrGraph graph, int root, int owner) {
        final BitSet visited = new BitSet();
        final IntArrayList stack = new IntArrayList();
        stack.add(graph.getValue().getOperands(root).getInt(0));
        while (!stack.isEmpty()) {
            final int id = stack.popInt();
            if (visited.get(id)) continue;
            visited.set(id);
            final IrNode node = graph.getNode(id);
            if (node instanceof KernelNode || node instanceof ConstantPoolNode) continue;
            check(!(node instanceof ProgramNode || node instanceof KernelRootNode),
                    "HIR body root %d consumes structural node %d as a value or metadata", root, id);
            if (node instanceof KernelArgumentNode) {
                check(kernelOwner(graph, id) == owner,
                        "Kernel body root %d uses argument %d owned by a different kernel", root, id);
                continue;
            }
            stack.addAll(graph.getValue().getOperands(id));
            stack.addAll(graph.getMemory().getOperands(id));
        }
    }

    private static void verifyPrograms(IrGraph graph, int start, int end) {
        final EdgeAccessor control = graph.getControl();
        final String entryKind = "FunctionBeginNode";

        final IntList entries = control.getUsages(start);
        int programEntries = 0;
        for (int i = 0; i < entries.size(); i++) {
            final int entry = entries.getInt(i);
            final IrNode node = graph.getNode(entry);
            if (node instanceof ConstantPoolNode) continue;
            check(node instanceof FunctionBeginNode,
                    "Program start %d may only point to %s entries and the constant pool in MIR, got %s at %d",
                    start, entryKind, name(graph, entry), entry);
            programEntries++;
        }
        check(programEntries > 0,
                "Program start %d must point to at least one %s entry", start, entryKind);

        // END collects ReturnNodes and the constant pool only
        final IntList returns = control.getOperands(end);
        for (int i = 0; i < returns.size(); i++) {
            final int ret = returns.getInt(i);
            final IrNode node = graph.getNode(ret);
            check(node == ReturnNode.RET || node instanceof ConstantPoolNode,
                    "Program end %d may only be pointed to by ReturnNode and the constant pool, got %s at %d",
                    end, name(graph, ret), ret);
        }

        // The program table mirrors the entry/exit pairs of the control edges
        check(graph.countPrograms() == programEntries,
                "Defined program count %d does not match %d %s entries under START",
                graph.countPrograms(), programEntries, entryKind);
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
                case ConstantPoolNode ignored -> {
                    // verified by verifyConstantPool
                }
                case FunctionBeginNode ignored -> verifyEntry(graph, id, start);
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
            if (node instanceof ControlNode && !(node instanceof ProgramNode)
                    && !(node instanceof ConstantPoolNode)) {
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
            case KernelRootNode ignored -> "KernelRootNode(%s)".formatted(functionName(graph, id));
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
            if (state != null && state.node() instanceof KernelNode descriptor) {
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
