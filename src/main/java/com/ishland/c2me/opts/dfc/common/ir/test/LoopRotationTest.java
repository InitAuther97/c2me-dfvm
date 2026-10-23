package com.ishland.c2me.opts.dfc.common.ir.test;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.graph.cfg.CFGAccess;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.BranchBeginNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.FunctionBeginNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.function.FunctionNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.IfBranchNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.MergeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ReturnNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.branch.PhiNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.value.TestFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant.DoubleConstantNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.AddFPNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.cfg.LoopRotation;
import com.ishland.c2me.opts.dfc.common.ir.test.framework.Assert;
import it.unimi.dsi.fastutil.ints.IntList;

/// Verifies loop rotation: a loop whose header ends with an exit check and
/// whose back edge carries scheduled computations is rotated into a guarded
/// loop with the check at the end of the latch. Covers both the general
/// shape and the empty-body shape where the body branch is itself the latch.
@SuppressWarnings("unused")
public class LoopRotationTest {

    /// Builds {@code entry -> H { phi; if (cond) body else exit }; body ->
    /// latch -> H; exit -> ret }, with vBack scheduled on the latch block.
    /// Returns ids: [entry, ret, H, ifOld, btBody, btExit, latch, zero, phi, vBack, cond].
    private static int[] buildLoop(GraphContext ctx, boolean emptyBody) {
        final IrGraph graph = ctx.getIrGraph();
        final var control = graph.getControl();
        final var value = graph.getValue();
        final int descriptor = graph.addNode(new FunctionNode("test"));
        final int entry = graph.addNode(FunctionBeginNode.BEGIN);
        graph.getMemory().appendEdge(descriptor, entry);
        final int ret = graph.addNode(ReturnNode.RET);
        //                     v----------------------^
        // Begin -> Entry -> Merge -> if -> true -> merge
        control.appendEdge(ctx.getProgramStart(), entry);
        control.appendEdge(ret, ctx.getProgramEnd());

        final int H = graph.addNode(MergeNode.MERGE);
        final int ifOld = graph.addNode(IfBranchNode.BRANCH);
        final int btBody = graph.addNode(BranchBeginNode.TRUE);
        final int btExit = graph.addNode(BranchBeginNode.FALSE);
        final int latch = emptyBody ? btBody : graph.addNode(MergeNode.MERGE);

        control.appendEdge(entry, H);
        control.appendEdge(H, ifOld);
        control.appendEdge(ifOld, btBody);
        control.appendEdge(ifOld, btExit);
        if (!emptyBody) control.appendEdge(btBody, latch);
        control.appendEdge(latch, H);
        control.appendEdge(btExit, ret);

        final int zero = graph.addNode(new DoubleConstantNode(0));
        final int one = graph.addNode(new DoubleConstantNode(1));
        final int nine = graph.addNode(new DoubleConstantNode(9));
        final int phi = graph.addNode(PhiNode.PHI);
        final int vBack = graph.addNode(AddFPNode.ADD_D);
        final int cond = graph.addNode(TestFPNode.MIN_INCLUSIVE_MAX_INCLUSIVE);

        control.appendEdge(H, phi);
        value.appendEdge(zero, phi);
        value.appendEdge(vBack, phi);
        value.appendEdge(phi, vBack);
        value.appendEdge(one, vBack);
        value.appendEdge(phi, cond);
        value.appendEdge(zero, cond);
        value.appendEdge(nine, cond);
        value.appendEdge(cond, ifOld);
        value.appendEdge(phi, ret);

        // Scheduling, mimicking late scheduling: phi inputs on the edge they
        // flow through, loop check at the header
        control.appendEdge(entry, zero);
        control.appendEdge(entry, nine);
        control.appendEdge(latch, vBack);
        control.appendEdge(latch, one);
        control.appendEdge(H, cond);

        graph.defineProgram(entry, ret);
        return new int[]{entry, ret, H, ifOld, btBody, btExit, latch, zero, phi, vBack, cond};
    }

    private static void assertRotated(GraphContext ctx, int[] ids, boolean emptyBody) {
        final IrGraph graph = ctx.getIrGraph();
        final var control = graph.getControl();
        final var value = graph.getValue();
        final int ret = ids[1], H = ids[2], ifOld = ids[3];
        final int latch = ids[6], zero = ids[7], phi = ids[8], vBack = ids[9], cond = ids[10];

        final var cfg = graph.getOrComputeCFGAt(0, CFGAccess.Status.LOOP_BODY);

        // Exactly one loop remains, with an empty latch block so the loop
        // does not qualify for rotation again
        final var headers = cfg.loopHeaderIterator();
        Assert.assertTrue(headers.hasNext(), "a loop should remain");
        final int headerIdx = headers.nextInt();
        Assert.assertFalse(headers.hasNext(), "only one loop should remain");
        final var loop = cfg.getLoopFromHeader(headerIdx);
        Assert.assertNotNull(loop, "loop lookup by header");
        Assert.assertEquals(1, control.getUsages(cfg.getBlockBegin(loop.latch())).size(),
                "new latch block should be empty");

        // The header is now reached through the guard body and the new latch
        final IntList hOps = control.getOperands(H);
        Assert.assertEquals(2, hOps.size(), "header keeps two control inputs");
        Assert.assertTrue(graph.getNode(hOps.getInt(0)) instanceof BranchBeginNode,
                "header preheader edge comes from the guard");
        Assert.assertTrue(graph.getNode(hOps.getInt(1)) instanceof BranchBeginNode,
                "header back edge comes from the new latch");

        // The reused if node is now the latch check on the back edge, with
        // the condition translated to the latch-side phi input
        Assert.assertEquals(emptyBody ? H : latch, control.getOperands(ifOld).getInt(0),
                "if should be scheduled at the end of the back edge");
        final int latchCond = value.getOperands(ifOld).getInt(0);
        Assert.assertTrue(latchCond != cond, "latch check should use a translated condition");
        Assert.assertEquals(vBack, value.getOperands(latchCond).getInt(0),
                "translated latch condition should read the back-edge value");

        // The exit value is repaired by a phi merging both exit paths
        final int repaired = value.getOperands(ret).getInt(0);
        Assert.assertTrue(repaired != phi, "exit usage should be rewired to a repair phi");
        Assert.assertSame(PhiNode.PHI, graph.getNode(repaired), "repair should be a phi");
        final IntList repairInputs = value.getOperands(repaired);
        Assert.assertEquals(zero, repairInputs.getInt(0), "guard-side repair input");
        Assert.assertEquals(vBack, repairInputs.getInt(1), "latch-side repair input");
    }

    public void testLoopRotation() {
        final GraphContext ctx = new GraphContext();
        final int[] ids = buildLoop(ctx, false);
        new LoopRotation(ctx).run();
        assertRotated(ctx, ids, false);
        assertIdempotent(ctx);
    }

    public void testLoopRotationEmptyBody() {
        final GraphContext ctx = new GraphContext();
        final int[] ids = buildLoop(ctx, true);
        new LoopRotation(ctx).run();
        assertRotated(ctx, ids, true);
        assertIdempotent(ctx);
    }

    public void testEmptyBackEdgeNotRotated() {
        final GraphContext ctx = new GraphContext();
        final int[] ids = buildLoop(ctx, false);
        // Move the back-edge computations into the header, mimicking a loop
        // check that uses them: the back edge is now empty, no rotation
        final IrGraph graph = ctx.getIrGraph();
        final int latch = ids[6], H = ids[2], vBack = ids[9];
        final int one = graph.getValue().getOperands(vBack).getInt(1);
        graph.getControl().replaceEdge(vBack, latch, H);
        graph.getControl().replaceEdge(one, latch, H);
        final int mod = graph.modCount();
        new LoopRotation(ctx).run();
        Assert.assertEquals(mod, graph.modCount(), "loop with empty back edge should not rotate");
    }

    private static void assertIdempotent(GraphContext ctx) {
        final IrGraph graph = ctx.getIrGraph();
        final int mod = graph.modCount();
        new LoopRotation(ctx).run();
        Assert.assertEquals(mod, graph.modCount(), "rotation should be idempotent");
    }
}