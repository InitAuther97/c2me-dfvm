package com.ishland.c2me.opts.dfc.common.ir.phase.hir;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.HighTierPhase;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.RunCanonicalizerPhase;

/// Base for HIR phases that run the canonicalizer. In addition to
/// {@link RunCanonicalizerPhase}, every {@link #canonicalize()} re-seeds all
/// kernel descriptors into the work queue and repeats until the graph stops
/// changing, so the iterative solve of kernel return types (see
/// {@link KernelNode#compute}) happens transparently for every HIR
/// phase that canonicalizes. One round propagates one level of the
/// kernel-call hierarchy. This dependency deliberately stays out of the
/// graph: linking the returned value back to the descriptor would close a
/// cycle through its argument ownership markers and the kernel body. Body
/// results are looked up through KernelRootNode, without a MIR program table.
public class HIRCanonicalizationPhase extends RunCanonicalizerPhase implements HighTierPhase {

    private final GraphContext ctx;

    public HIRCanonicalizationPhase(GraphContext ctx) {
        super(ctx);
        this.ctx = ctx;
    }

    @Override
    public void run() {
        canonicalize();
    }

    @Override
    protected void canonicalize() {
        final IrGraph graph = ctx.getIrGraph();
        int lastModCount;
        do {
            lastModCount = graph.modCount();
            seedDescriptors(graph);
            super.canonicalize();
        } while (graph.modCount() != lastModCount);
    }

    private void seedDescriptors(IrGraph graph) {
        for (int id = 0; id < graph.nodes().size(); id++) {
            if (graph.get(id) != null && graph.getNode(id) instanceof KernelNode) {
                enqueue(id);
            }
        }
    }
}
