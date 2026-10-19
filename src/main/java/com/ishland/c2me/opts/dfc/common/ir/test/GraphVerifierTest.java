package com.ishland.c2me.opts.dfc.common.ir.test;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.graph.GraphVerifier;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.phase.PhaseStage;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.BranchBeginNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.FunctionBeginNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.FunctionNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.IfBranchNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.MergeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ReturnNode;
import com.ishland.c2me.opts.dfc.common.ir.test.framework.Assert;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.BooleanType;

/// Verifies {@link GraphVerifier}: a straight-line program and an if/else
/// diamond pass, while broken control spines (missing edges, swapped branch
/// order, programs not flowing into a single return) are rejected.
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

    private static void expectViolation(String name, Runnable action) {
        try {
            action.run();
        } catch (AssertionError e) {
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

    public void testSchedulingEdgesAreTolerated() {
        final GraphContext ctx = buildDiamond();
        final IrGraph graph = ctx.getIrGraph();
        // Pin a value node to a block; scheduling edges carry no requirements
        final int value = graph.addNode(new OpaqueInputNode(BooleanType.BOT));
        final int entry = graph.getControl().getUsages(ctx.getProgramStart()).getInt(0);
        graph.getControl().appendEdge(entry, value);
        GraphVerifier.verify(ctx, PhaseStage.MIR);
    }

    public void testStartWithoutEntryFails() {
        expectViolation("empty program", () -> GraphVerifier.verify(new GraphContext(), PhaseStage.MIR));
    }

    public void testIfBranchWithOneSuccessorFails() {
        final GraphContext ctx = buildDiamond();
        final IrGraph graph = ctx.getIrGraph();
        final var control = graph.getControl();
        final int ret = control.getOperands(ctx.getProgramEnd()).getInt(0);
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
        final int ret = control.getOperands(ctx.getProgramEnd()).getInt(0);
        final int merge = control.getOperands(ret).getInt(0);
        final int btFalse = control.getOperands(merge).getInt(1);
        control.removeEdge(btFalse, merge);
        expectViolation("merge with one predecessor", () -> GraphVerifier.verify(ctx, PhaseStage.MIR));
    }

    public void testReturnNotConnectedToEndFails() {
        final GraphContext ctx = buildStraightLine();
        final IrGraph graph = ctx.getIrGraph();
        final int ret = graph.getControl().getOperands(ctx.getProgramEnd()).getInt(0);
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