package com.ishland.c2me.opts.dfc.common.ir.graph;

import com.ishland.c2me.opts.dfc.common.ir.LongUtil;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.LinkedList;
import java.util.function.IntConsumer;

public interface EdgeAccessor {

    ThreadLocal<LinkedList<LongArrayList>> WORKSPACE_CACHE = ThreadLocal.withInitial(LinkedList::new);

    int allocate();

    int allocate(int count);

    IntList getUsages(int id);

    IntList getOperands(int id);

    void appendEdge(int from, int to);

    void removeEdge(int from, int to);

    void setIncoming(int node, int index, int operand);

    void addEdgePair(int from, int idx, int to, int idx2);

    void replaceEdge(int node, int originalOp, int newOp);
    
    void insertEdgeMiddleman(int from, int to, int mid);

    void insertEdge(int node, int index, int operand);

    void bulkReplaceSrc(int node, Int2IntFunction replace);

    default boolean isOrphan(int node) {
        return getUsages(node).isEmpty();
    }

    void copyFrom(EdgeList other);

    void purge();

    void compress(IntList mapping, int actualSize);

    static EdgeAccessor immutable(EdgeAccessor accessor) {
        return new Immutable(accessor);
    }

    static EdgeAccessor reversedImmutable(EdgeAccessor accessor) {
        return new ReversedImmutable(accessor);
    }

    class Immutable implements EdgeAccessor {
        protected final EdgeAccessor delegate;

        public Immutable(EdgeAccessor delegate) {
            this.delegate = delegate;
        }

        static void trap() {
            throw new UnsupportedOperationException("Modifying immutable EdgeAccessor");
        }

        @Override
        public int allocate() {
            trap();
            return -1;
        }

        @Override
        public int allocate(int count) {
            trap();
            return -1;
        }

        @Override
        public IntList getUsages(int id) {
            return delegate.getUsages(id);
        }

        @Override
        public IntList getOperands(int id) {
            return delegate.getOperands(id);
        }

        @Override
        public void appendEdge(int from, int to) {
            trap();
        }

        @Override
        public void removeEdge(int from, int to) {
            trap();
        }

        @Override
        public void setIncoming(int node, int index, int operand) {
            trap();
        }

        @Override
        public void addEdgePair(int from, int idx, int to, int idx2) {
            trap();
        }

        @Override
        public void replaceEdge(int node, int originalOp, int newOp) {
            trap();
        }

        @Override
        public void insertEdgeMiddleman(int from, int to, int mid) {
            trap();
        }

        @Override
        public void insertEdge(int node, int index, int operand) {
            trap();
        }

        @Override
        public void bulkReplaceSrc(int node, Int2IntFunction replace) {
            trap();
        }

        @Override
        public void copyFrom(EdgeList other) {
            trap();
        }

        @Override
        public void purge() {
            trap();
        }

        @Override
        public void compress(IntList mapping, int actualSize) {
            trap();
        }
    }

    abstract class TransformingImmutable extends Immutable implements EdgeAccessor {

        public TransformingImmutable(EdgeAccessor delegate) {
            super(delegate);
        }

        protected abstract int transform(int node);

        @Override
        public IntList getUsages(int id) {
            return super.getUsages(transform(id));
        }

        @Override
        public IntList getOperands(int id) {
            return super.getOperands(transform(id));
        }
    }

    class ReversedImmutable extends Immutable {

        ReversedImmutable(EdgeAccessor delegate) {
            super(delegate);
        }

        @Override
        public IntList getUsages(int id) {
            return super.getOperands(id);
        }

        @Override
        public IntList getOperands(int id) {
            return super.getUsages(id);
        }
    }

    default void walk(int begin, DepthFirstVisitor visitor) {
        final LongArrayList stack;
        final var workspace = WORKSPACE_CACHE.get();
        if (workspace.isEmpty()) {
            stack = new LongArrayList();
        } else {
            stack = workspace.pop();
        }
        if (!visitor.enter(begin)) return;
        stack.push(LongUtil.make(begin, 0));
        while (!stack.isEmpty()) {
            final long next = stack.popLong();
            final int current = LongUtil.getLow(next);
            final int index = LongUtil.getHigh(next);
            final var list = getUsages(current);
            if (index == list.size()) {
                visitor.leave(current);
                continue;
            }
            stack.push(LongUtil.make(current, index + 1));
            final int child = list.getInt(index);
            if (!visitor.enter(child)) continue;
            stack.push(LongUtil.make(child, 0));
        }
        workspace.push(stack);
    }

    interface DepthFirstVisitor {
        /// @return true if it should skip the nodes' downstream
        boolean enter(int node);
        void leave(int node);

        static DepthFirstVisitor onEnter(IntConsumer visitor) {
            return new OnEnter(visitor);
        }
        
        static DepthFirstVisitor onLeave(IntConsumer visitor) {
            return new OnLeave(visitor);
        }

        record OnEnter(IntConsumer visitor) implements DepthFirstVisitor {
            @Override
            public boolean enter(int node) {
                visitor.accept(node);
                return true;
            }

            @Override
            public void leave(int node) {
            }
        }

        record OnLeave(IntConsumer visitor) implements DepthFirstVisitor {
            @Override
            public boolean enter(int node) {
                return true;
            }

            @Override
            public void leave(int node) {
                visitor.accept(node);
            }
        }
    }
}
