package com.ishland.c2me.opts.dfc.common.ir.phase.scalar;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.phase.OptimizationPhase;

public class InitialCanonicalizationPhase implements OptimizationPhase {

    private final GraphContext ctx;

    public InitialCanonicalizationPhase(GraphContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void run() {

    }
}
