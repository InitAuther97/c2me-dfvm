package com.ishland.c2me.opts.dfc.common.ir.phase.scalar;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.phase.OptimizationPhase;

public abstract class RunCanonicalizerPhase implements OptimizationPhase {
    private final Canonicalizer canonicalizer;

    protected RunCanonicalizerPhase(GraphContext ctx) {
        this.canonicalizer = new Canonicalizer(ctx);
    }

    protected void enqueue(int i) {
        canonicalizer.addToWorkQueue(i);
    }

    protected void canonicalize() {
        canonicalizer.run();
    }
}
