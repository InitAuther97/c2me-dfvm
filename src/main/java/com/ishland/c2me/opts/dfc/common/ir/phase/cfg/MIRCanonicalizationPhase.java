package com.ishland.c2me.opts.dfc.common.ir.phase.cfg;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.FunctionNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.MidTierPhase;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.RunCanonicalizerPhase;

/// Base for MIR phases that run the canonicalizer, mirroring
/// {@link com.ishland.c2me.opts.dfc.common.ir.phase.hir.HIRCanonicalizationPhase}.
/// In addition to {@link RunCanonicalizerPhase}, every {@link #canonicalize()}
/// re-seeds all function descriptors into the work queue and repeats until
/// the graph stops changing, so descriptor-driven information stays in sync
/// for every MIR phase that canonicalizes.
public class MIRCanonicalizationPhase extends RunCanonicalizerPhase implements MidTierPhase {

    private final GraphContext ctx;

    public MIRCanonicalizationPhase(GraphContext ctx) {
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
            seedFunctions(graph);
            super.canonicalize();
        } while (graph.modCount() != lastModCount);
    }

    private void seedFunctions(IrGraph graph) {
        for (int id = 0; id < graph.nodes().size(); id++) {
            if (graph.get(id) != null && graph.getNode(id) instanceof FunctionNode) {
                enqueue(id);
            }
        }
    }
}