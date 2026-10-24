package com.ishland.c2me.opts.dfc.common.ir.test;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.LongUtil;
import com.ishland.c2me.opts.dfc.common.ir.graph.GraphVerifier;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.CoordinateNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelRootNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.value.TestINode;
import com.ishland.c2me.opts.dfc.common.ir.phase.PhaseStage;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.BeginBlockNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.BranchBeginNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.FunctionBeginNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.function.FunctionNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.IfBranchNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.MergeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ReturnNode;
import com.ishland.c2me.opts.dfc.common.ir.test.framework.Assert;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.BooleanType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.IntegerType;

/// Verifies {@link GraphVerifier}: a straight-line program and an if/else
/// diamond pass, while broken control spines (missing edges, swapped branch
/// order, programs not flowing into a single return) and HIR nodes in MIR
/// are rejected. Shared scalar operations remain valid in MIR.
@SuppressWarnings("unused")
public class GraphVerifierTest {

    /// Builds {@code START -> entry -> ret -> END}.
    private static GraphContext buildStraightLine() {
        return new GraphFixture().context();
    }

    /// Builds {@code START -> entry -> if -> {true, false} -> merge -> ret -> END}.
    private static GraphContext buildDiamond() {
        final GraphContext ctx = new GraphContext();
        final IrGraph graph = ctx.getIrGraph();
        final var control = graph.getControl();
        final int descriptor = graph.addNode(new FunctionNode("test"));
        final int entry = graph.addNode(FunctionBeginNode.BEGIN);
        graph.getMemory().appendEdge(descriptor, entry);
        final int ret = graph.addNode(ReturnNode.RET);
        final int ifNode = graph.addNode(IfBranchNode.BRANCH);
        final int btTrue = graph.addNode(BranchBeginNode.TRUE);
        final int btFalse = graph.addNode(BranchBeginNode.FALSE);
        final int merge = graph.addNode(MergeNode.MERGE);
        final int cond = graph.addNode(new OpaqueInputNode(BooleanType.BOT));
        control.appendEdge(ctx.getProgramStart(), entry);
        control.appendEdge(entry, ifNode);
        control.appendEdge(ifNode, btTrue);
        control.appendEdge(ifNode, btFalse);
        control.appendEdge(btTrue, merge);
        control.appendEdge(btFalse, merge);
        control.appendEdge(merge, ret);
        control.appendEdge(ret, ctx.getProgramEnd());
        graph.getValue().appendEdge(cond, ifNode);
        graph.defineProgram(entry, ret);
        return ctx;
    }

    private static void expectViolation(String name, Runnable action, String... expectedMessages) {
        try {
            action.run();
        } catch (AssertionError e) {
            for (String expected : expectedMessages) {
                Assert.assertTrue(e.getMessage() != null && e.getMessage().contains(expected),
                        name + ": unexpected violation: " + e.getMessage());
            }
            return;
        }
        Assert.fail(name + ": expected AssertionError from GraphVerifier");
    }

    public void testStraightLineProgramPasses() {
        GraphVerifier.verify(buildStraightLine(), PhaseStage.MIR);
    }

    public void testDiamondPasses() {
        GraphVerifier.verify(buildDiamond(), PhaseStage.MIR);
    }

    public void testSharedScalarNodesPassInMir() {
        final GraphFixture fixture = new GraphFixture();
        final int value = fixture.iadd(fixture.input(IntegerType.BOT), fixture.intConst(1));
        fixture.keepAlive(value);
        GraphVerifier.verify(fixture.context(), PhaseStage.MIR);
    }

    public void testHirValueNodeFailsInMir() {
        final GraphFixture fixture = new GraphFixture();
        final int value = fixture.node(TestINode.MIN_INCLUSIVE_MAX_EXCLUSIVE,
                fixture.input(IntegerType.BOT), fixture.intConst(0), fixture.intConst(10));
        fixture.keepAlive(value);
        expectViolation("HIR value returned from MIR",
                () -> GraphVerifier.verify(fixture.context(), PhaseStage.MIR),
                "MIR graph must not contain HIR node TestINode at " + value);
    }

    public void testUnusedHirNodesFailInMir() {
        final HIRNode[] nodes = {
                new KernelNode("unused"),
                KernelRootNode.ROOT,
                new CoordinateNode(0, 0, 0)
        };
        for (HIRNode node : nodes) {
            final GraphContext ctx = buildStraightLine();
            GraphVerifier.verify(ctx, PhaseStage.MIR);
            ctx.getIrGraph().addNode(node);
            expectViolation("unused " + node.getClass().getSimpleName() + " in MIR",
                    () -> GraphVerifier.verify(ctx, PhaseStage.MIR),
                    "MIR graph must not contain HIR node", node.getClass().getSimpleName());
        }
    }

    public void testSchedulingEdgesFollowControlSuccessors() {
        final GraphContext ctx = buildDiamond();
        final IrGraph graph = ctx.getIrGraph();
        final var control = graph.getControl();
        for (int id = 0; id < graph.nodes().size(); id++) {
            if (!(graph.getNode(id) instanceof BeginBlockNode)) continue;
            final int value = graph.addNode(new OpaqueInputNode(BooleanType.BOT));
            final int terminator = control.getUsages(id).getInt(0);
            final int operandIndex = control.getOperands(terminator).indexOf(id);
            control.appendEdge(id, value); // Scheduling follows the block terminator.
            GraphVerifier.verify(ctx, PhaseStage.MIR);

            control.removeEdge(id, terminator);
            control.appendEdge(id, terminator); // A terminator after scheduling is invalid.
            expectViolation("scheduling before terminator", () -> GraphVerifier.verify(ctx, PhaseStage.MIR),
                    "must place control-flow successors before scheduling edges");
            control.removeEdge(id, terminator);
            control.addEdgePair(id, 0, terminator, operandIndex);
        }
    }

    public void testStartWithoutEntryFails() {
        expectViolation("empty program", () -> GraphVerifier.verify(new GraphContext(), PhaseStage.MIR));
    }

    public void testIfBranchWithOneSuccessorFails() {
        final GraphContext ctx = buildDiamond();
        final IrGraph graph = ctx.getIrGraph();
        final var control = graph.getControl();
        final int ret = LongUtil.getHigh(graph.getProgramAt(0));
        final int merge = control.getOperands(ret).getInt(0);
        final int btFalse = control.getOperands(merge).getInt(1);
        final int ifNode = control.getOperands(btFalse).getInt(0);
        control.removeEdge(ifNode, btFalse);
        expectViolation("if with one successor", () -> GraphVerifier.verify(ctx, PhaseStage.MIR));
    }

    public void testSwappedBranchOrderFails() {
        final GraphContext ctx = new GraphContext();
        final IrGraph graph = ctx.getIrGraph();
        final var control = graph.getControl();
        final int descriptor = graph.addNode(new FunctionNode("test"));
        final int entry = graph.addNode(FunctionBeginNode.BEGIN);
        graph.getMemory().appendEdge(descriptor, entry);
        final int ret = graph.addNode(ReturnNode.RET);
        final int ifNode = graph.addNode(IfBranchNode.BRANCH);
        final int btTrue = graph.addNode(BranchBeginNode.TRUE);
        final int btFalse = graph.addNode(BranchBeginNode.FALSE);
        final int merge = graph.addNode(MergeNode.MERGE);
        control.appendEdge(ctx.getProgramStart(), entry);
        control.appendEdge(entry, ifNode);
        control.appendEdge(ifNode, btFalse); // FALSE at slot 0: wrong order
        control.appendEdge(ifNode, btTrue);
        control.appendEdge(btTrue, merge);
        control.appendEdge(btFalse, merge);
        control.appendEdge(merge, ret);
        control.appendEdge(ret, ctx.getProgramEnd());
        graph.defineProgram(entry, ret);
        expectViolation("swapped branch order", () -> GraphVerifier.verify(ctx, PhaseStage.MIR));
    }

    public void testMergeWithOnePredecessorFails() {
        final GraphContext ctx = buildDiamond();
        final IrGraph graph = ctx.getIrGraph();
        final var control = graph.getControl();
        final int ret = LongUtil.getHigh(graph.getProgramAt(0));
        final int merge = control.getOperands(ret).getInt(0);
        final int btFalse = control.getOperands(merge).getInt(1);
        control.removeEdge(btFalse, merge);
        expectViolation("merge with one predecessor", () -> GraphVerifier.verify(ctx, PhaseStage.MIR));
    }

    public void testReturnNotConnectedToEndFails() {
        final GraphContext ctx = buildStraightLine();
        final IrGraph graph = ctx.getIrGraph();
        final int ret = LongUtil.getHigh(graph.getProgramAt(0));
        graph.getControl().removeEdge(ret, ctx.getProgramEnd());
        expectViolation("return not flowing into END", () -> GraphVerifier.verify(ctx, PhaseStage.MIR));
    }

    public void testUndefinedProgramFails() {
        final GraphContext ctx = buildStraightLine();
        final IrGraph graph = ctx.getIrGraph();
        // A second entry under START that was never defineProgram-ed
        final int descriptor2 = graph.addNode(new FunctionNode("other"));
        final int entry2 = graph.addNode(FunctionBeginNode.BEGIN);
        graph.getMemory().appendEdge(descriptor2, entry2);
        final int ret2 = graph.addNode(ReturnNode.RET);
        graph.getControl().appendEdge(ctx.getProgramStart(), entry2);
        graph.getControl().appendEdge(entry2, ret2);
        graph.getControl().appendEdge(ret2, ctx.getProgramEnd());
        expectViolation("program missing from the program table", () -> GraphVerifier.verify(ctx, PhaseStage.MIR));
    }

    public void testUnreachableControlNodeFails() {
        final GraphContext ctx = buildDiamond();
        final IrGraph graph = ctx.getIrGraph();
        // A disconnected branch pair not reachable from any entry
        final int ifNode = graph.addNode(IfBranchNode.BRANCH);
        final int btTrue = graph.addNode(BranchBeginNode.TRUE);
        final int btFalse = graph.addNode(BranchBeginNode.FALSE);
        graph.getControl().appendEdge(ifNode, btTrue);
        graph.getControl().appendEdge(ifNode, btFalse);
        expectViolation("unreachable control nodes", () -> GraphVerifier.verify(ctx, PhaseStage.MIR));
    }
}
