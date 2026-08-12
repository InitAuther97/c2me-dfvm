package com.ishland.c2me.opts.dfc.common.ir.graph;

import it.unimi.dsi.fastutil.ints.IntList;

public class PostOrderCollector extends PostOrderVisitor implements EdgeAccessor.DepthFirstVisitor, AutoCloseable {
    private static final ThreadLocal<PostOrderCollector> WORKSPACE_CACHE = ThreadLocal.withInitial(PostOrderCollector::new);

    static PostOrderCollector begin(IntList postOrder, int end) {
        final var result = WORKSPACE_CACHE.get();
        result.postOrder = postOrder;
        result.end = end;
        return result;
    }

    public static void populatePostOrder(EdgeAccessor edges, int begin, int end, IntList postOrder) {
        // We start from the beginning, and walks the def->use edges until we reach the end
        // When a node's children are all finished, we add this node into a list
        // We do this in a depth first manner, creating a post-order list
        try (final var po = begin(postOrder, end)) {
            edges.walk(begin, po);
        }
    }

    private IntList postOrder;
    private int end;

    private PostOrderCollector() {
    }

    @Override
    public boolean enter(int node) {
        if (node == end) {
            leave(node);
            return false;
        }
        return super.enter(node);
    }

    @Override
    public void leave(int node) {
        assert postOrder != null : "INTERNAL ERROR: RPOIterator.Workspace#list is null during graph walking";
        postOrder.add(node);
    }

    @Override
    public void close() {
        super.reset();
        postOrder = null;
    }
}
