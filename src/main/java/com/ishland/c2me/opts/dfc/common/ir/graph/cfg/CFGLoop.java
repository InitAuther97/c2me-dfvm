package com.ishland.c2me.opts.dfc.common.ir.graph.cfg;

import java.util.BitSet;

public record CFGLoop(
        int header,
        int latch,
        BitSet blocks,
        BitSet exits
) {}
