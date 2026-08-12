package com.ishland.c2me.opts.dfc.common.ir.phase.cfg;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.graph.cfg.CFGAccess;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ControlNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.MidTierPhase;
import com.ishland.flowsched.util.Assertions;

import java.util.BitSet;

public interface SchedulePhase extends MidTierPhase {
    default BitSet[] collectNodeScheduling(GraphContext ctx, int programIdx) {
        final var graph = ctx.getIrGraph();
        final var control = graph.getControl();
        final var cfg = graph.getOrComputeCFGAt(programIdx, CFGAccess.Status.BASIC_BLOCK);
        final int size = cfg.countBasicBlocks();
        final var scheduling = new BitSet[size];
        for (int i = 0; i < size; i++) {
            final int begin = cfg.getBlockBegin(i);
            final var bitset = new BitSet(graph.size());
            scheduling[i] = bitset;
            final var usages = control.getUsages(begin);
            int j = 0;
            for (; j < usages.size(); j++) {
                if (!(graph.getNode(usages.getInt(j)) instanceof ControlNode)) break;
            }
            for (; j < usages.size(); j++) {
                final int downstream = usages.getInt(j);
                bitset.set(downstream);
                control.removeEdge(begin, downstream);
            }
        }
        return scheduling;
    }

    default void applyNodeScheduling(GraphContext ctx, int programIdx, BitSet[] scheduling) {
        final var graph = ctx.getIrGraph();
        final var control = graph.getControl();
        final var cfg = graph.getOrComputeCFGAt(programIdx, CFGAccess.Status.BASIC_BLOCK);
        final int size = cfg.countBasicBlocks();
        Assertions.assertTrue(size == scheduling.length, "CFG changed before and after node scheduling collection");
        for (int i = 0; i < size; i++) {
            final int begin = cfg.getBlockBegin(i);
            final var bitset = scheduling[i];
            int j = -1;
            while ((j = bitset.nextSetBit(j + 1)) >= 0) {
                control.appendEdge(begin, j);
            }
        }
    }
}
