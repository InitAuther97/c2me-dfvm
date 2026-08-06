package com.ishland.c2me.opts.dfc.common.ir.phase.cfg.schedule;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;

import java.util.BitSet;

public interface SchedulePhase {
    void schedule(GraphContext graph, int programIndex, BitSet[] scheduling);
}
