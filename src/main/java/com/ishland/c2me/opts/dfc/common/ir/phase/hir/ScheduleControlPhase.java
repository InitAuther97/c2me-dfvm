package com.ishland.c2me.opts.dfc.common.ir.phase.hir;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.phase.HighTierPhase;

/// This phase transforms nodes with complex sampling logic (Interpolated, FindTopSurface, etc.)
/// and converts them into nested loop structure, reducing overall memory overhead.
/// This phase currently splits interpolator behind interpolator into multiple DFs and
/// cache it if necessary. This replaces a general way to fuse interpolator loops as it's hard to
/// reason and code.
public class ScheduleControlPhase implements HighTierPhase {

    private final GraphContext ctx;

    public ScheduleControlPhase(GraphContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void run() {
        final var graph = ctx.getIrGraph();
    }


}
