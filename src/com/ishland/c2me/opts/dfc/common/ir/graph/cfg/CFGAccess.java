package com.ishland.c2me.opts.dfc.common.ir.graph.cfg;

import com.ishland.c2me.opts.dfc.common.ir.LongUtil;
import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeAccessor;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.graph.PostOrderCollector;
import com.ishland.c2me.opts.dfc.common.util.ArrayUtil;
import com.ishland.c2me.opts.dfc.common.util.BitSetIterator;
import com.ishland.flowsched.util.Assertions;
import it.unimi.dsi.fastutil.Stack;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;

public sealed interface CFGAccess {
    Comparator<CFGLoop> BY_SIZE = Comparator.comparing(CFGLoop::blocks,
            Comparator.comparingInt(BitSet::cardinality));
    Comparator<CFGLoop> BY_HEADER = Comparator.comparingInt(CFGLoop::header);

    ThreadLocal<Workspace> WORKSPACE_CACHE = ThreadLocal.withInitial(Workspace::new);

    boolean isAtLeast(Status<?> status);

    record Workspace(IntArrayList po, BitSet visited, Stack<IntArrayList> stack) {
        Workspace() {
            this(new IntArrayList(), new BitSet(), new ReferenceArrayList<>());
        }
    }

    static <T extends CFGAccess> T build(IrGraph graph, int programIdx, CFGAccess initial, Status<T> target) {
        final var base = (Base) initial;
        final int basic = base == null ? -1 : base.idx;
        final int max = target.idx;
        assert initial == null || !initial.isAtLeast(target) : "No need to build";
        int begin = -1, end = -1, endBlock = -1;
        long[] basicBlocks = null;
        BitSet mergeBlocks = null, splitBlocks = null;
        EdgeAccessor cfg = null;
        DomTree dom = null, pdom = null;
        CFGLoop[] loops = null;
        int[] blockToLoop = null;
        long[] parentAndDepth = null;
        BitSet loopHeaders = null;
        switch (basic) {
            case 4:
                final var body = (LoopBody) base;
                loops = body.loops;
                // fallthrough
            case 3:
                final var header = (LoopHeader) base;
                loopHeaders = header.loopHeaders;
                // fallthrough
            case 2:
                final var dominator = (Dominator) base;
                dom = dominator.dom;
                pdom = dominator.pdom;
                // fallthrough
            case 1:
                final var fg = (FlowGraph) base;
                mergeBlocks = fg.mergeBlocks;
                splitBlocks = fg.splitBlocks;
                cfg = fg.cfg;
                // fallthrough
            case 0:
                final var bb = (BasicBlock) base;
                begin = bb.begin;
                end = bb.end;
                endBlock = bb.endBlock;
                basicBlocks = bb.basicBlocks;
                // fallthrough
            case -1:
                break;
            default:
                throw new IllegalArgumentException("Input initial CFGAccess has too high status than expected");
        }

        final var workspace = WORKSPACE_CACHE.get();
        final var po = workspace.po;
        final var visited = workspace.visited;
        switch (basic) {
            case -1:
                final long program = graph.getProgramAt(programIdx);
                begin = LongUtil.getLow(program);
                end = LongUtil.getHigh(program);
                int bbs = CFGHelper.countBlocks(graph, begin, end, po);
                basicBlocks = new long[bbs];
                endBlock = CFGHelper.identifyBasicBlocks(graph, po, visited, begin, end, basicBlocks);
                Assertions.assertTrue(endBlock != -1, "CFGAccess invariant assertion failed: end block not found");
                if (max == 0) break;
                // fallthrough
            case 0:
                assert basicBlocks != null;
                // Build CFG
                mergeBlocks = new BitSet(basicBlocks.length);
                splitBlocks = new BitSet(basicBlocks.length);
                cfg = CFGHelper.buildCFG(graph.getControl(), basicBlocks, mergeBlocks, splitBlocks, end);
                // basicBlocks is sorted by now, refresh the end block index
                endBlock = CFGHelper.findEndBlock(basicBlocks, end);
                if (max == 1) break;
                // fallthrough
            case 1:
                Assertions.assertTrue(LongUtil.findByLow(basicBlocks, begin) == 0, "CFGAccess invariant assertion failed: begin block is not the first block");
                // It is OK to use -1 because we don't have anything after our end in CFG
                PostOrderCollector.populatePostOrder(cfg, 0, -1, po);
                dom = DomTree.createDomTree(cfg, po, 0);
                po.clear();

                final var invCfg = EdgeAccessor.reversedImmutable(cfg);
                PostOrderCollector.populatePostOrder(invCfg, endBlock, -1, po);
                pdom = DomTree.createDomTree(invCfg, po, endBlock);
                po.clear();
                if (max == 2) break;
                // fallthrough
            case 2:
                loopHeaders = new BitSet(basicBlocks.length >> 1);
                CFGHelper.identifyLoops(basicBlocks, dom, loopHeaders);
                if (max == 3) break;
                // fallthrough
            case 3:
                // Then, we collect all basic blocks belonging to a loop
                // This works by going backward from the latch block and mark all blocks
                // encountered until we reach the header block
                final int loopCount = loopHeaders.cardinality();
                loops = new CFGLoop[loopCount];
                CFGHelper.collectLoopBlocks(cfg, loopHeaders, basicBlocks, dom, po, loops);
                if (max == 4) break;
                // fallthrough
            case 4:
                // Establish loop nesting relation
                // We sort the loops by size; for every loop, the first containing loop
                // going forward is the exact parent we wanted
                Arrays.sort(loops, BY_SIZE);
                parentAndDepth = new long[loops.length];
                Arrays.fill(parentAndDepth, Integer.toUnsignedLong(-1));
                CFGHelper.buildLoopNest(loops, parentAndDepth);

                // Compute loop depth
                CFGHelper.buildLoopDepth(parentAndDepth);

                // Assign blocks to loop
                blockToLoop = new int[basicBlocks.length];
                CFGHelper.assignToLoop(loops, blockToLoop);
                break;
        }
        return target.cast(switch (max) {
            case 0 -> new BasicBlock(begin, end, endBlock, basicBlocks);
            case 1 -> new FlowGraph(begin, end, endBlock, basicBlocks, mergeBlocks, splitBlocks, cfg);
            case 2 -> new Dominator(begin, end, endBlock, basicBlocks, mergeBlocks, splitBlocks, cfg, dom, pdom);
            case 3 -> new LoopHeader(begin, end, endBlock, basicBlocks, mergeBlocks, splitBlocks, cfg, dom, pdom, loopHeaders);
            case 4 -> new LoopBody(begin, end, endBlock, basicBlocks, mergeBlocks, splitBlocks, cfg, dom, pdom, loopHeaders, loops);
            case 5 -> new LoopNest(begin, end, endBlock, basicBlocks, mergeBlocks, splitBlocks, cfg, dom, pdom, loopHeaders, loops, blockToLoop, parentAndDepth);
            default -> throw new IllegalArgumentException("Target status is unknown: " + max);
        });
    }

    class Status<T extends CFGAccess> {

        private static <T extends CFGAccess> Status<T> create(int idx, Class<T> clazz) {
            return new Status<>(idx, clazz);
        }

        private final int idx;
        private final Class<T> clazz;

        private Status(int idx, Class<T> clazz) {
            this.idx = idx;
            this.clazz = clazz;
        }

        public T cast(CFGAccess access) {
            return clazz.cast(access);
        }

        public boolean is(CFGAccess access) {
            return clazz.isInstance(access);
        }

        public static final Status<BasicBlock> BASIC_BLOCK = create(0, BasicBlock.class);
        public static final Status<FlowGraph> FLOW_GRAPH = create(1, FlowGraph.class);
        public static final Status<Dominator> DOMINATOR = create(2, Dominator.class);
        public static final Status<LoopHeader> LOOP_HEADER = create(3, LoopHeader.class);
        public static final Status<LoopBody> LOOP_BODY = create(4, LoopBody.class);
        public static final Status<LoopNest> LOOP_NEST = create(5, LoopNest.class);
    }

    sealed class Base implements CFGAccess {
        private final int idx;

        Base(int idx) {
            this.idx = idx;
        }

        @Override
        public boolean isAtLeast(Status<?> status) {
            return idx >= status.idx;
        }
    }

    sealed class BasicBlock extends Base {
        protected final int begin, end;
        protected final int endBlock;
        protected final long[] basicBlocks;

        protected BasicBlock(int idx, int begin, int end, int endBlock, long[] basicBlocks) {
            super(idx);
            this.begin = begin;
            this.end = end;
            this.endBlock = endBlock;
            this.basicBlocks = basicBlocks;
        }

        BasicBlock(int begin, int end, int endBlock, long[] basicBlocks) {
            this(Status.BASIC_BLOCK.idx, begin, end, endBlock, basicBlocks);
        }

        public final int getEndBlock() {
            return endBlock;
        }

        public final int getBlockBegin(int bbIdx) {
            return LongUtil.getLow(getBasicBlock(bbIdx));
        }

        public final int getBlockEnd(int bbIdx) {
            return LongUtil.getHigh(getBasicBlock(bbIdx));
        }

        public final long getBasicBlock(int bbIdx) {
            Assertions.assertTrue(bbIdx >= 0 && bbIdx < basicBlocks.length, "basic block not found");
            return this.basicBlocks[bbIdx];
        }

        public LongIterator basicBlockIterator() {
            return LongIterators.wrap(basicBlocks);
        }

        public int countBasicBlocks() {
            return basicBlocks.length;
        }
    }

    sealed class FlowGraph extends BasicBlock {
        protected final BitSet mergeBlocks;
        protected final BitSet splitBlocks;
        protected final EdgeAccessor cfg;

        protected FlowGraph(int idx, int begin, int end, int endBlock, long[] basicBlocks,
                  BitSet mergeBlocks, BitSet splitBlocks, EdgeAccessor cfg) {
            super(idx, begin, end, endBlock, basicBlocks);
            this.mergeBlocks = mergeBlocks;
            this.splitBlocks = splitBlocks;
            this.cfg = cfg;
        }

        FlowGraph(int begin, int end, int endBlock, long[] basicBlocks,
                  BitSet mergeBlocks, BitSet splitBlocks, EdgeAccessor cfg) {
            this(Status.FLOW_GRAPH.idx, begin, end, endBlock, basicBlocks, mergeBlocks, splitBlocks, cfg);
        }

        public final boolean isIdxMergeBlock(int bbIdx) {
            Assertions.assertTrue(bbIdx >= 0 && bbIdx < basicBlocks.length, "basic block not found");
            return mergeBlocks.get(bbIdx);
        }

        public final boolean isIdxSplitBlock(int bbIdx) {
            Assertions.assertTrue(bbIdx >= 0 && bbIdx < basicBlocks.length, "basic block not found");
            return splitBlocks.get(bbIdx);
        }

        public final IntIterator mergeBlockIterator() {
            return new BitSetIterator(mergeBlocks);
        }

        public final IntIterator splitBlockIterator() {
            return new BitSetIterator(splitBlocks);
        }

        public final EdgeAccessor getCFG() {
            return EdgeAccessor.immutable(this.cfg);
        }
    }

    sealed class Dominator extends FlowGraph {
        protected final DomTree dom;
        protected final DomTree pdom;

        protected Dominator(int idx, int begin, int end, int endBlock, long[] basicBlocks,
                            BitSet mergeBlocks, BitSet splitBlocks, EdgeAccessor cfg,
                            DomTree dom, DomTree pdom) {
            super(idx, begin, end, endBlock, basicBlocks, mergeBlocks, splitBlocks, cfg);
            this.dom = dom;
            this.pdom = pdom;
        }

        Dominator(int begin, int end, int endBlock, long[] basicBlocks,
                  BitSet mergeBlocks, BitSet splitBlocks, EdgeAccessor cfg,
                  DomTree dom, DomTree pdom) {
            this(Status.DOMINATOR.idx, begin, end, endBlock, basicBlocks, mergeBlocks, splitBlocks, cfg, dom, pdom);
        }

        public final DomTree getDomTree() {
            return dom;
        }

        public final DomTree getPdomTree() {
            return pdom;
        }

        public final boolean isDominatedBy(int controlThis, int controlThat) {
            final int idThis = LongUtil.findByLow(basicBlocks, controlThis);
            final int idThat = LongUtil.findByLow(basicBlocks, controlThat);
            return isBlockDominatedBy(idThis, idThat);
        }

        public final boolean isBlockDominatedBy(int blockThis, int blockThat) {
            Assertions.assertTrue(blockThis >= 0 && blockThis < basicBlocks.length
                    && blockThat >= 0 && blockThat < basicBlocks.length, "basic block not found");
            return dom.isDominatedBy(blockThis, blockThat);
        }

        public final boolean isPostDominatedBy(int controlThis, int controlThat) {
            final int idThis = LongUtil.findByLow(basicBlocks, controlThis);
            final int idThat = LongUtil.findByLow(basicBlocks, controlThat);
            return isBlockPostDominatedBy(idThis, idThat);
        }

        public final boolean isBlockPostDominatedBy(int blockThis, int blockThat) {
            Assertions.assertTrue(blockThis >= 0 && blockThis < basicBlocks.length
                    && blockThat >= 0 && blockThat < basicBlocks.length, "basic block not found");
            return pdom.isDominatedBy(blockThis, blockThat);
        }
    }

    sealed class LoopHeader extends Dominator {

        protected final BitSet loopHeaders;

        protected LoopHeader(int idx, int begin, int end, int endBlock, long[] basicBlocks,
                             BitSet mergeBlocks, BitSet splitBlocks, EdgeAccessor cfg,
                             DomTree dom, DomTree pdom, BitSet loopHeaders) {
            super(idx, begin, end, endBlock, basicBlocks, mergeBlocks, splitBlocks, cfg, dom, pdom);
            this.loopHeaders = loopHeaders;
        }

        LoopHeader(int begin, int end, int endBlock, long[] basicBlocks,
                   BitSet mergeBlocks, BitSet splitBlocks, EdgeAccessor cfg,
                   DomTree dom, DomTree pdom, BitSet loopHeaders) {
            this(Status.LOOP_HEADER.idx, begin, end, endBlock, basicBlocks, mergeBlocks, splitBlocks, cfg, dom, pdom, loopHeaders);
        }

        public final boolean isBlockLoopHeader(int blockIdx) {
            return loopHeaders.get(blockIdx);
        }

        public final IntIterator loopHeaderIterator() {
            return new BitSetIterator(loopHeaders);
        }
    }

    sealed class LoopBody extends LoopHeader {

        protected final CFGLoop[] loops;

        protected LoopBody(int idx, int begin, int end, int endBlock, long[] basicBlocks,
                           BitSet mergeBlocks, BitSet splitBlocks, EdgeAccessor cfg,
                           DomTree dom, DomTree pdom, BitSet loopHeaders,
                           CFGLoop[] loops) {
            super(idx, begin, end, endBlock, basicBlocks, mergeBlocks, splitBlocks, cfg, dom, pdom, loopHeaders);
            this.loops = loops;
        }

        LoopBody(int begin, int end, int endBlock, long[] basicBlocks,
                 BitSet mergeBlocks, BitSet splitBlocks, EdgeAccessor cfg,
                 DomTree dom, DomTree pdom, BitSet loopHeaders,
                 CFGLoop[] loops) {
            this(Status.LOOP_BODY.idx, begin, end, endBlock, basicBlocks, mergeBlocks, splitBlocks, cfg, dom, pdom, loopHeaders, loops);
        }

        public CFGLoop getLoopFromHeader(int headerIdx) {
            return ArrayUtil.binarySearch(loops, CFGLoop::header, headerIdx);
        }
    }

    final class LoopNest extends LoopBody {

        private final int[] blockToLoop;
        private final long[] parentAndDepth;

        LoopNest(int begin, int end, int endBlock, long[] basicBlocks,
                 BitSet mergeBlocks, BitSet splitBlocks, EdgeAccessor cfg,
                 DomTree dom, DomTree pdom, BitSet loopHeaders,
                 CFGLoop[] loops, int[] blockToLoop, long[] parentAndDepth) {
            super(Status.LOOP_NEST.idx, begin, end, endBlock, basicBlocks, mergeBlocks, splitBlocks, cfg, dom, pdom, loopHeaders, loops);
            this.blockToLoop = blockToLoop;
            this.parentAndDepth = parentAndDepth;
        }

        @Override
        public CFGLoop getLoopFromHeader(int headerIdx) {
            return loops[blockToLoop[headerIdx]];
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
    }
}
