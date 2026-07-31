package com.ishland.c2me.opts.dfc.common.ir.graph.cfg;

import com.ishland.c2me.opts.dfc.common.ir.LongUtil;
import com.ishland.c2me.opts.dfc.common.ir.graph.*;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.BeginBlockNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.ControlNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg.EndBlockNode;
import com.ishland.flowsched.util.Assertions;
import it.unimi.dsi.fastutil.Stack;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntLongBiConsumer;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;

public class CFGInfo {

    private static final Comparator<CFGLoop> BY_SIZE = Comparator.comparing(CFGLoop::blocks,
            Comparator.comparingInt(BitSet::cardinality));

    private static final ThreadLocal<Workspace> WORKSPACE_CACHE = ThreadLocal.withInitial(Workspace::new);

    record Workspace(IntArrayList po, BitSet visited, Stack<IntArrayList> stack) {
        Workspace() {
            this(new IntArrayList(), new BitSet(), new ReferenceArrayList<>());
        }
    }

    private final int beginNode, endNode, endBlock;
    private final long[] basicBlocks; // high: end (32bit) | begin (32bit)
    private final BitSet mergeBlocks;
    private final EdgeAccessor cfg;
    private final DomTree dom;
    private final DomTree pdom;

    private final CFGLoop[] loops;
    private final int[] blockToLoop;
    private final long[] parentAndDepth; // high: depth (32bit) | parent (32bit)

    private CFGInfo(int begin, int end, int endBlock, long[] basicBlocks,
                    BitSet mergeBlocks, EdgeAccessor cfg, DomTree dom,
                    DomTree pdom, CFGLoop[] loops, int[] blockToLoop,
                    long[] parentAndDepth) {
        this.beginNode = begin;
        this.endNode = end;
        this.endBlock = endBlock;
        this.basicBlocks = basicBlocks;
        this.mergeBlocks = mergeBlocks;
        this.cfg = cfg;
        this.dom = dom;
        this.pdom = pdom;
        this.loops = loops;
        this.blockToLoop = blockToLoop;
        this.parentAndDepth = parentAndDepth;
    }

    public int getBlockBegin(int bbIdx) {
        return LongUtil.getLow(getBasicBlock(bbIdx));
    }

    public int getBlockEnd(int bbIdx) {
        return LongUtil.getHigh(getBasicBlock(bbIdx));
    }

    public long getBasicBlock(int bbIdx) {
        Assertions.assertTrue(bbIdx >= 0 && bbIdx < basicBlocks.length, "basic block not found");
        return this.basicBlocks[bbIdx];
    }

    public int getLoopDepth(int beginNode) {
        final int bbIdx = LongUtil.findByLow(basicBlocks, beginNode);
        Assertions.assertTrue(bbIdx >= 0 && bbIdx < basicBlocks.length, "basic block not found");
        return getLoopDepthForBlock(bbIdx);
    }

    public int getLoopDepthForBlock(int bbIdx) {
        Assertions.assertTrue(bbIdx >= 0 && bbIdx < basicBlocks.length, "basic block not found");
        final int lIdx = blockToLoop[bbIdx];
        if (lIdx == -1) return 0;
        final long pAndD = parentAndDepth[lIdx];
        return LongUtil.getHigh(pAndD) + 1;
    }

    public boolean isDominatedBy(int controlThis, int controlThat) {
        final int idThis = LongUtil.findByLow(basicBlocks, controlThis);
        final int idThat = LongUtil.findByLow(basicBlocks, controlThat);
        return isBlockDominatedBy(idThis, idThat);
    }

    public boolean isBlockDominatedBy(int blockThis, int blockThat) {
        Assertions.assertTrue(blockThis >= 0 && blockThis < basicBlocks.length
                        && blockThat >= 0 && blockThat < basicBlocks.length, "basic block not found");
        return dom.isDominatedBy(blockThis, blockThat);
    }

    public boolean isPostDominatedBy(int controlThis, int controlThat) {
        final int idThis = LongUtil.findByLow(basicBlocks, controlThis);
        final int idThat = LongUtil.findByLow(basicBlocks, controlThat);
        return isBlockPostDominatedBy(idThis, idThat);
    }

    public boolean isBlockPostDominatedBy(int blockThis, int blockThat) {
        Assertions.assertTrue(blockThis >= 0 && blockThis < basicBlocks.length
                && blockThat >= 0 && blockThat < basicBlocks.length, "basic block not found");
        return pdom.isDominatedBy(blockThis, blockThat);
    }

    public boolean isVeryBusy(int controlThis, int controlThat) {
        final int idThis = LongUtil.findByLow(basicBlocks, controlThis);
        final int idThat = LongUtil.findByLow(basicBlocks, controlThat);
        return isBlockVeryBusy(idThis, idThat);
    }

    public boolean isBlockVeryBusy(int blockThis, int blockThat) {
        Assertions.assertTrue(blockThis >= 0 && blockThis < basicBlocks.length
                && blockThat >= 0 && blockThat < basicBlocks.length, "basic block not found");
        final var upstreamThis = cfg.getOperands(blockThis);
        final var upstreamThat = cfg.getOperands(blockThat);
        for (int i = 0; i < upstreamThis.size(); i++) {
            if (upstreamThat.contains(upstreamThis.getInt(i)))
                return true;
        }
        return false;
    }

    public boolean isMergeBlock(int bbIdx) {
        Assertions.assertTrue(bbIdx >= 0 && bbIdx < basicBlocks.length, "basic block not found");
        return mergeBlocks.get(bbIdx);
    }

    public int getEndBlock() {
        return endBlock;
    }

    public DomTree getDomTree() {
        return dom;
    }

    public DomTree getPdomTree() {
        return pdom;
    }

    public void walkInDominance(IntLongBiConsumer visitor) {
        final var workspace = WORKSPACE_CACHE.get().stack;
        final var array = workspace.pop();
        final var list = array == null ? new IntArrayList() : array;
        dom.walk(list::add);
        for (int i = 0; i < list.size(); i++) {
            visitor.accept(i, this.basicBlocks[i]);
        }
        list.clear();
        workspace.push(list);
    }

    public void walkInRPO(IntLongBiConsumer visitor) {
        final var workspace = WORKSPACE_CACHE.get().stack;
        final var array = workspace.pop();
        final var list = array == null ? new IntArrayList() : array;
        PostOrderVisitor.populatePostOrder(cfg, this.beginNode, this.endNode, list);
        for (int i = list.size() - 1; i >= 0; i--) {
            visitor.accept(i, this.basicBlocks[i]);
        }
        list.clear();
        workspace.push(list);
    }

    public EdgeAccessor getCFG() {
        return EdgeAccessor.immutable(this.cfg);
    }

    public static CFGInfo create(IrGraph graph, int begin, int end) {
        final var control = graph.getControl();
        final var workspace = WORKSPACE_CACHE.get();
        final var po = workspace.po;
        final var visited = workspace.visited;
        // Collect basic block count
        final int size = countBlocks(graph, begin, end, po);
        // Collect basic blocks
        long[] bb = new long[size];
        final int endBlock = identifyBasicBlocks(graph, po, visited, begin, end, bb);
        Assertions.assertTrue(endBlock != -1, "CFGInfo invariant assertion failed: end block not found");
        // Build CFG
        BitSet mergeBlocks = new BitSet(size);
        final var cfg = buildCFG(control, bb, mergeBlocks);

        // Build dominator tree and post-dominator tree
        Assertions.assertTrue(LongUtil.findByLow(bb, begin) == 0, "CFGInfo invariant assertion failed: begin block is not the first block");
        // It is OK to use -1 because we don't have anything after our end
        PostOrderVisitor.populatePostOrder(cfg, 0, -1, po);
        final var domTree = DomTree.createDomTree(cfg, po, 0);
        po.clear();

        final var invCfg = EdgeAccessor.reversedImmutable(cfg);
        PostOrderVisitor.populatePostOrder(invCfg, endBlock, -1, po);
        final var pdomTree = DomTree.createDomTree(invCfg, po, endBlock);
        po.clear();

        // Begin loop info computation
        // First, for every loop begin, it corresponds to a loop structure
        // For all input control of a Merge, if one is dominated by the Merge,
        // the edge is a back-edge and the Merge corresponds to a loop.
        // Here we assume the back-edge is on the second place, which is proper.
        // The structure of IR determines that only one latch exists for a loop.

        //noinspection UnnecessaryLocalVariable
        final var stack = po;
        //noinspection UnnecessaryLocalVariable
        final var loopBitset = visited;
        identifyLoops(bb, domTree, loopBitset);

        // Then, we collect all basic blocks belonging to a loop
        // This works by going backward from the latch block and mark all blocks
        // encountered until we reach the header block
        final int loopCount = loopBitset.cardinality();
        final var loops = new CFGLoop[loopCount];
        collectLoopBlocks(cfg, loopBitset, bb, stack, loops);

        // Establish loop nesting relation
        // We sort the loops by size; for every loop, the first containing loop
        // going forward is the exact parent we wanted
        Arrays.sort(loops, BY_SIZE);
        final long[] parentAndDepth = new long[loopCount];
        Arrays.fill(parentAndDepth, Integer.toUnsignedLong(-1));
        buildLoopNest(loops, parentAndDepth);

        // Compute loop depth
        buildLoopDepth(parentAndDepth);

        // Assign blocks to loop
        final int[] blockToLoop = new int[size];
        assignToLoop(loops, blockToLoop);

        loopBitset.clear();
        return new CFGInfo(begin, end, endBlock, bb, mergeBlocks, cfg, domTree, pdomTree, loops, blockToLoop, parentAndDepth);
    }

    private static int countBlocks(IrGraph graph, int begin, int end, IntArrayList po) {
        PostOrderVisitor.populatePostOrder(graph.getControl(), begin, end, po);
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

    private static int identifyBasicBlocks(IrGraph graph, IntArrayList stack, BitSet visited, int begin, int end, long[] bb) {
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

    private static EdgeAccessor buildCFG(EdgeAccessor control, long[] bb, BitSet mergeBlocks) {
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
                // This is a split block, we have multiple successors
                // (in our context, there will be only two block)
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

    private static void identifyLoops(long[] bb, DomTree dom, BitSet loop) {
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

    private static void collectLoopBlocks(EdgeAccessor cfg, BitSet loops, long[] bb, IntArrayList stack, CFGLoop[] loopArray) {
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

    private static void buildLoopNest(CFGLoop[] loops, long[] parentAndDepth) {
        for (int i = 0; i < loops.length; i++) {
            final int header = loops[i].header();
            int parent = i;
            //noinspection StatementWithEmptyBody
            while (++parent < loops.length && !loops[parent].blocks().get(header));
            if (parent == loops.length) continue;
            parentAndDepth[i] = parent;
        }
    }

    private static void buildLoopDepth(long[] parentAndDepth) {
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

    private static void assignToLoop(CFGLoop[] loops, int[] blockToLoop) {
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
