package com.ishland.c2me.opts.dfc.common.ir.graph;

import it.unimi.dsi.fastutil.ints.IntList;

import java.util.BitSet;

public class PostOrderVisitor implements EdgeAccessor.DepthFirstVisitor, AutoCloseable {
    private static final ThreadLocal<PostOrderVisitor> WORKSPACE_CACHE = ThreadLocal.withInitial(PostOrderVisitor::new);

    static PostOrderVisitor begin(IntList postOrder, int end) {
        final var result = WORKSPACE_CACHE.get();
        result.postOrder = postOrder;
        result.end = end;
        return result;
    }

    private final BitSet bitset;
    private IntList postOrder;
    private int end;

    private PostOrderVisitor() {
        this.bitset = new BitSet();
    }

    public static void populatePostOrder(EdgeAccessor edges, int begin, int end, IntList postOrder) {
        // We start from the beginning, and walks the def->use edges until we reach the end
        // When a node's children are all finished, we add this node into a list
        // We do this in a depth first manner, creating a post-order list
        try (final var po = begin(postOrder, end)) {
            edges.walk(begin, po);
        }
    }

    @Override
    public boolean enter(int node) {
        if (bitset.get(node)) {
            return false;
        }
        bitset.set(node);
        if (node == end) {
            leave(node);
            return false;
        }
        return true;
    }

    @Override
    public void leave(int node) {
        assert postOrder != null : "INTERNAL ERROR: RPOIterator.Workspace#list is null during graph walking";
        postOrder.add(node);
    }

    @Override
    public void close() {
        bitset.clear();
        postOrder = null;
    }
}
