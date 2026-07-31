package com.ishland.c2me.opts.dfc.common.ir.phase.scalar;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.phase.OptimizationPhase;

public abstract class PostRunCanonicalizerPhase implements OptimizationPhase {
    private final Canonicalizer canonicalizer;

    protected PostRunCanonicalizerPhase(GraphContext ctx) {
        this.canonicalizer = new Canonicalizer(ctx);
    }

    protected void enqueue(int i) {
        canonicalizer.addToWorkQueue(i);
    }
}
