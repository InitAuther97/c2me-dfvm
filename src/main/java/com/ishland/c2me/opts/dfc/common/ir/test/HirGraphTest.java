package com.ishland.c2me.opts.dfc.common.ir.test;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.graph.GraphVerifier;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelArgumentNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelInvokeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelRootNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.PackedVolumeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ReturnNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.memory.LoadNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.branch.PhiNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant.IntConstantNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.AddINode;
import com.ishland.c2me.opts.dfc.common.ir.phase.PhaseStage;
import com.ishland.c2me.opts.dfc.common.ir.phase.hir.HIRCanonicalizationPhase;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.GVN;
import com.ishland.c2me.opts.dfc.common.ir.stamp.ForeignKernelStamp;
import com.ishland.c2me.opts.dfc.common.ir.test.framework.Assert;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.IntegerType;

import java.util.List;

/// HIR bodies are descriptor-owned value DAGs, independent of MIR programs.
@SuppressWarnings("unused")
public class HirGraphTest {

    private record Body(int descriptor, int argument, int root) {
    }

    private static Body body(GraphContext ctx, String name) {
        final IrGraph graph = ctx.getIrGraph();
        final int descriptor = graph.addNode(new KernelNode(name));
        final int argument = graph.addNode(new KernelArgumentNode("x", IntegerType.range(1, 10)));
        graph.getMemory().appendEdge(descriptor, argument);
        return new Body(descriptor, argument, root(ctx, descriptor, argument));
    }

    private static int root(GraphContext ctx, int descriptor, int result) {
        final IrGraph graph = ctx.getIrGraph();
        final int root = graph.addNode(KernelRootNode.ROOT);
        graph.getMemory().appendEdge(descriptor, root);
        graph.getValue().appendEdge(result, root);
        graph.getControl().appendEdge(ctx.getProgramStart(), root);
        graph.getControl().appendEdge(root, ctx.getProgramEnd());
        return root;
    }

    private static int volume(IrGraph graph, int coordinate) {
        final int volume = graph.addNode(PackedVolumeNode.INSTANCE);
        final int one = graph.addNode(new IntConstantNode(1));
        for (int i = 0; i < 9; i++) {
            graph.getValue().appendEdge(i == 3 ? coordinate : one, volume);
        }
        return volume;
    }

    private static int invoke(IrGraph graph, int callee, int volume) {
        final int invoke = graph.addNode(KernelInvokeNode.INVOKE);
        graph.getMemory().appendEdge(callee, invoke);
        graph.getMemory().appendEdge(volume, invoke);
        return invoke;
    }

    private static void expectViolation(GraphContext ctx, String message) {
        try {
            GraphVerifier.verify(ctx, PhaseStage.HIR);
        } catch (AssertionError e) {
            Assert.assertTrue(e.getMessage().contains(message), "Unexpected violation: " + e.getMessage());
            return;
        }
        Assert.fail("Expected HIR violation containing: " + message);
    }

    public void testValueDagHasNoMirProgram() {
        final GraphContext ctx = new GraphContext();
        final Body body = body(ctx, "sample");
        GraphVerifier.verify(ctx, PhaseStage.HIR);
        Assert.assertEquals(0, ctx.getIrGraph().countPrograms(), "HIR must not register executable programs");
        Assert.assertEquals(IntegerType.range(1, 10),
                KernelRootNode.ROOT.compute(ctx.getIrGraph(), body.root(), ctx), "Root exposes its result type");
    }

    public void testRootRequiresDescriptorAndOneResult() {
        final GraphContext ctx = new GraphContext();
        final Body body = body(ctx, "sample");
        ctx.getIrGraph().getValue().removeEdge(body.argument(), body.root());
        expectViolation(ctx, "exactly one result");
    }

    public void testRootRejectsMultipleResults() {
        final GraphContext ctx = new GraphContext();
        final Body body = body(ctx, "sample");
        ctx.getIrGraph().getValue().appendEdge(body.argument(), body.root());
        expectViolation(ctx, "exactly one result");
    }

    public void testRootMustReachGlobalEnd() {
        final GraphContext ctx = new GraphContext();
        final Body body = body(ctx, "sample");
        ctx.getIrGraph().getControl().removeEdge(body.root(), ctx.getProgramEnd());
        expectViolation(ctx, "START -> root -> END");
    }

    public void testBodyDescriptorRequiresExactlyOneRoot() {
        final GraphContext ctx = new GraphContext();
        final Body body = body(ctx, "sample");
        root(ctx, body.descriptor(), body.argument());
        expectViolation(ctx, "must own 1 body roots");
    }

    public void testArgumentRequiresOwnerMarker() {
        final GraphContext ctx = new GraphContext();
        final Body body = body(ctx, "sample");
        ctx.getIrGraph().getMemory().removeEdge(body.descriptor(), body.argument());
        expectViolation(ctx, "exactly one KernelNode memory operand");
    }

    public void testRootRequiresOwnerMarker() {
        final GraphContext ctx = new GraphContext();
        final Body body = body(ctx, "sample");
        ctx.getIrGraph().getMemory().removeEdge(body.descriptor(), body.root());
        expectViolation(ctx, "must own 1 body roots");
    }

    public void testBodyRejectsAnotherKernelsArgument() {
        final GraphContext ctx = new GraphContext();
        final Body first = body(ctx, "first");
        final Body second = body(ctx, "second");
        ctx.getIrGraph().getValue().replaceEdge(first.root(), first.argument(), second.argument());
        expectViolation(ctx, "owned by a different kernel");
    }

    public void testPackedVolumeCannotCaptureAnotherKernelsArgument() {
        final GraphContext ctx = new GraphContext();
        final IrGraph graph = ctx.getIrGraph();
        final Body first = body(ctx, "first");
        final Body second = body(ctx, "second");
        final int call = invoke(graph, second.descriptor(), volume(graph, second.argument()));
        graph.getValue().replaceEdge(first.root(), first.argument(), call);
        expectViolation(ctx, "owned by a different kernel");
    }

    public void testSharedSubexpressionsAreAllowed() {
        final GraphContext ctx = new GraphContext();
        final IrGraph graph = ctx.getIrGraph();
        final Body body = body(ctx, "sample");
        final int shared = graph.addNode(AddINode.ADD_I);
        graph.getValue().appendEdge(body.argument(), shared);
        graph.getValue().appendEdge(body.argument(), shared);
        final int result = graph.addNode(AddINode.ADD_I);
        graph.getValue().appendEdge(shared, result);
        graph.getValue().appendEdge(shared, result);
        graph.getValue().replaceEdge(body.root(), body.argument(), result);
        GraphVerifier.verify(ctx, PhaseStage.HIR);
    }

    public void testValueCyclesAreRejected() {
        final GraphContext ctx = new GraphContext();
        final IrGraph graph = ctx.getIrGraph();
        final Body body = body(ctx, "sample");
        final int cyclic = graph.addNode(AddINode.ADD_I);
        graph.getValue().appendEdge(body.argument(), cyclic);
        graph.getValue().appendEdge(cyclic, cyclic);
        graph.getValue().replaceEdge(body.root(), body.argument(), cyclic);
        expectViolation(ctx, "must form a DAG");
    }

    public void testMirControlFlowIsRejected() {
        final GraphContext ctx = new GraphContext();
        body(ctx, "sample");
        ctx.getIrGraph().addNode(ReturnNode.RET);
        expectViolation(ctx, "must not contain explicit control flow");
    }

    public void testMirPhisAreRejected() {
        final GraphContext ctx = new GraphContext();
        body(ctx, "sample");
        ctx.getIrGraph().addNode(PhiNode.PHI);
        expectViolation(ctx, "must not contain explicit control flow");
    }

    public void testMirMemoryOperationsAreRejected() {
        final GraphContext ctx = new GraphContext();
        body(ctx, "sample");
        ctx.getIrGraph().addNode(LoadNode.LOAD);
        expectViolation(ctx, "must not contain explicit control flow");
    }

    public void testValueNodesCannotBeScheduledInHir() {
        final GraphContext ctx = new GraphContext();
        final Body body = body(ctx, "sample");
        ctx.getIrGraph().getControl().appendEdge(body.root(), body.argument());
        expectViolation(ctx, "must not have control edges");
    }

    public void testForeignKernelHasNoRootAndKeepsDeclaredType() {
        final GraphContext ctx = new GraphContext();
        final IrGraph graph = ctx.getIrGraph();
        body(ctx, "sample");
        final KernelNode foreign = new KernelNode("foreign", IntegerType.BOT,
                new ForeignKernelStamp(ForeignKernelStamp.Kind.C_FUNCTION, "external", List.of(), List.of()));
        final int descriptor = graph.addNode(foreign);
        GraphVerifier.verify(ctx, PhaseStage.HIR);
        Assert.assertSame(IntegerType.BOT, foreign.compute(graph, descriptor, ctx), "Foreign type is declared");
        root(ctx, descriptor, graph.addNode(new IntConstantNode(7)));
        expectViolation(ctx, "must own 0 body roots");
    }

    public void testDescriptorReturnTypesPropagateThroughCallsWithoutPrograms() {
        final GraphContext ctx = new GraphContext();
        final IrGraph graph = ctx.getIrGraph();
        // Allocate callers first so propagation requires repeated descriptor rounds.
        final int outer = graph.addNode(new KernelNode("outer"));
        final int middle = graph.addNode(new KernelNode("middle"));
        final int leaf = graph.addNode(new KernelNode("leaf"));
        final int seven = graph.addNode(new IntConstantNode(7));
        final int packed = volume(graph, seven);
        final int middleCall = invoke(graph, leaf, packed);
        final int outerCall = invoke(graph, middle, packed);
        root(ctx, outer, outerCall);
        root(ctx, middle, middleCall);
        root(ctx, leaf, seven);
        new HIRCanonicalizationPhase(ctx).run();
        GraphVerifier.verify(ctx, PhaseStage.HIR);
        Assert.assertEquals(IntegerType.constant(7), graph.getType(outer), "Type propagates to outer caller");
        Assert.assertEquals(IntegerType.constant(7), graph.getType(middle), "Type propagates to middle caller");
        Assert.assertEquals(IntegerType.constant(7), graph.getType(leaf), "Type is inferred from leaf root");
        Assert.assertEquals(0, graph.countPrograms(), "Type inference must not need a MIR program table");
    }

    public void testArgumentValueNumberingRespectsKernelOwnership() {
        final GraphContext ctx = new GraphContext();
        final IrGraph graph = ctx.getIrGraph();
        final Body first = body(ctx, "first");
        final Body second = body(ctx, "second");
        final int duplicate = graph.addNode(new KernelArgumentNode("x", IntegerType.range(1, 10)));
        graph.getMemory().appendEdge(first.descriptor(), duplicate);
        final int result = graph.addNode(AddINode.ADD_I);
        graph.getValue().appendEdge(first.argument(), result);
        graph.getValue().appendEdge(duplicate, result);
        graph.getValue().replaceEdge(first.root(), first.argument(), result);
        new GVN(ctx).run();
        final var operands = graph.getValue().getOperands(result);
        Assert.assertEquals(operands.getInt(0), operands.getInt(1), "Same-owner arguments deduplicate");
        Assert.assertTrue(operands.getInt(0) != second.argument(), "Different-owner arguments stay distinct");
        GraphVerifier.verify(ctx, PhaseStage.HIR);
    }

    public void testRootAnchorSurvivesWithoutOutgoingUsages() {
        final GraphContext ctx = new GraphContext();
        final IrGraph graph = ctx.getIrGraph();
        final Body body = body(ctx, "sample");
        graph.getControl().removeEdge(body.root(), ctx.getProgramEnd());
        final Canonicalizer canonicalizer = new Canonicalizer(ctx);
        for (int id = 0; id < graph.nodes().size(); id++) canonicalizer.addToWorkQueue(id);
        canonicalizer.run();
        Assert.assertNotNull(graph.get(body.root()), "Body roots survive intermediate detached states");
        Assert.assertNotNull(graph.get(body.argument()), "The root keeps its body alive");
    }
}
