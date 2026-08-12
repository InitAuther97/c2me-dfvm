package com.ishland.c2me.opts.dfc.common.ir.graph.cfg;

import com.ishland.c2me.opts.dfc.common.ir.LongUtil;
import com.ishland.c2me.opts.dfc.common.ir.graph.*;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.BeginBlockNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ControlNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.EndBlockNode;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.Arrays;
import java.util.BitSet;

public class CFGHelper {

    static int countBlocks(IrGraph graph, int begin, int end, IntArrayList po) {
        PostOrderCollector.populatePostOrder(graph.getControl(), begin, end, po);
        int size = 0;
        for (int i = 0; i < po.size(); i++) {
            final int idx = po.getInt(i);
            if (graph.getNode(idx) instanceof BeginBlockNode) {
                size++;
            }
        }
        po.clear();
        return size;
    }

    static int identifyBasicBlocks(IrGraph graph, IntArrayList stack, BitSet visited, int begin, int end, long[] bb) {
        // Collect basic blocks
        // Note that a back-edge is also a basic block
        int idxBb = 0, endBlock = -1;
        stack.push(begin);
        final var control = graph.getControl();
        while (!stack.isEmpty()) {
            final int thus = stack.popInt();
            if (visited.get(thus) || thus == end) continue;
            visited.set(thus);
            final var node = graph.getNode(thus);
            if (!(node instanceof ControlNode)) continue;
            final var downstream = control.getUsages(thus);
            if (node instanceof BeginBlockNode) {
                final int endNode = downstream.getInt(0);
                assert graph.getNode(endNode) instanceof EndBlockNode :
                        "Begin block node points to nothing";
                bb[idxBb++] = LongUtil.make(thus, endNode);
                assert endNode != end || endBlock == -1 : "Multiple edge to end node";
                if (endNode == end) {
                    endBlock = idxBb;
                }
                stack.push(endNode);
            } else {
                // We may have multiple successors, just add them all
                stack.addAll(downstream);
            }
        }
        visited.clear();
        return endBlock;
    }

    static EdgeAccessor buildCFG(EdgeAccessor control, long[] bb, BitSet mergeBlocks, BitSet splitBlocks, int endBlock) {
        final var cfg = new EdgeList();
        // Sort by begin id to enable quick search
        // Later we use index to build cfg and trees
        cfg.allocate(bb.length);
        LongUtil.sortByLow(bb);

        // Build CFG
        for (int i = 0; i < bb.length; i++) {
            final long block = bb[i];
            final int stop = LongUtil.getHigh(block);
            final int idx = LongUtil.findByLow(bb, stop);
            if (idx < 0) {
                // Note that we can be at an end block, skip if that's the case
                if (i == endBlock) continue;
                // This is a split block, we have multiple successors
                // (in our context, there will be only two block)
                splitBlocks.set(i);
                final var successors = control.getUsages(stop);
                // Must guarantee that order of control edges is preserved
                for (int j = 0; j < successors.size(); j++) {
                    final int toBegin = successors.getInt(j);
                    final int toIdx = LongUtil.findByLow(bb, toBegin);
                    assert toIdx >= 0 : "Successor for CFG split is missing";
                    cfg.appendEdge(i, toIdx);
                }
            } else if (!mergeBlocks.get(idx)) {
                // idx is a merge block, we have multiple predecessors
                mergeBlocks.set(idx);
                final var predecessors = control.getOperands(stop);
                // Must guarantee that order of control edges is preserved
                for (int j = 0; j < predecessors.size(); j++) {
                    final int fromBegin = predecessors.getInt(j);
                    final int fromIdx = LongUtil.findByLow(bb, fromBegin);
                    assert fromIdx >= 0 : "Predecessor for CFG merge is missing";
                    cfg.appendEdge(fromIdx, idx);
                }
            }
        }
        return cfg;
    }

    static void identifyLoops(long[] bb, DomTree dom, BitSet loop) {
        for (int i = 0; i < bb.length; i++) {
            final long block = bb[i];
            // If an edge "goes back up", it is a back-edge, and the merge is a loop
            final int endAsBegin = LongUtil.findByLow(bb, LongUtil.getHigh(block));
            if (endAsBegin < 0) continue;
            final int beginIdx = LongUtil.findByLow(bb, LongUtil.getLow(block));
            if (!dom.isDominatedBy(beginIdx, endAsBegin)) continue;
            loop.set(i);
        }
    }

    static void collectLoopBlocks(EdgeAccessor cfg, BitSet loops, long[] bb, IntArrayList stack, CFGLoop[] loopArray) {
        int latchBlockIdx = -1, i = 0;
        while ((latchBlockIdx = loops.nextSetBit(latchBlockIdx + 1)) != -1) {
            final long latchBlock = bb[latchBlockIdx];
            final int beginNode = LongUtil.getHigh(latchBlock);
            final int headerBlockIdx = LongUtil.findByLow(bb, beginNode);
            final var blocks = new BitSet(bb.length);
            blocks.set(headerBlockIdx);
            stack.push(latchBlockIdx);
            while (!stack.isEmpty()) {
                final int thus = stack.popInt();
                if (blocks.get(thus) || thus == headerBlockIdx) continue;
                blocks.set(thus);
                stack.addAll(cfg.getOperands(thus));
            }
            final var exits = new BitSet();
            int idx = -1;
            while ((idx = blocks.nextSetBit(idx + 1)) != -1) {
                final var usages = cfg.getUsages(idx);
                for (int j = 0; j < usages.size(); j++) {
                    final int id = usages.getInt(j);
                    if (!blocks.get(id))
                        exits.set(id);
                }
            }
            loopArray[i++] = new CFGLoop(headerBlockIdx, latchBlockIdx, blocks, exits);
        }
    }

    static void buildLoopNest(CFGLoop[] loops, long[] parentAndDepth) {
        outer:
        for (int i = 0; i < loops.length; i++) {
            final int header = loops[i].header();
            int parent = i;
            while (!loops[++parent].blocks().get(header))
                if (parent == loops.length - 1) continue outer;
            parentAndDepth[i] = parent;
        }
    }

    static void buildLoopDepth(long[] parentAndDepth) {
        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < parentAndDepth.length; i++) {
                final long pAndD = parentAndDepth[i];
                final int parent = LongUtil.getLow(pAndD);
                if (parent == -1) continue;
                final int depth = LongUtil.getHigh(pAndD);
                final long parentPAndD = parentAndDepth[parent];
                final int parentDepth = LongUtil.getHigh(parentPAndD);
                if (depth - 1 == parentDepth) continue;
                parentAndDepth[i] = ((long) parentDepth + 1) << 32 | parent;
                changed = true;
            }
        } while (changed);
    }

    static void assignToLoop(CFGLoop[] loops, int[] blockToLoop) {
        Arrays.fill(blockToLoop, -1);
        for (int i = 0; i < loops.length; i++) {
            final var bitset = loops[i].blocks();
            int block = -1;
            while ((block = bitset.nextSetBit(block + 1)) != -1) {
                if (blockToLoop[block] == -1)
                    blockToLoop[block] = i;
            }
        }
    }

}
