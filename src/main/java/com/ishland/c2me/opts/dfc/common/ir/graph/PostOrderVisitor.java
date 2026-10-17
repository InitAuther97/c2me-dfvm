package com.ishland.c2me.opts.dfc.common.ir.graph;

import java.util.BitSet;

public abstract class PostOrderVisitor implements EdgeAccessor.DepthFirstVisitor {

    private final BitSet bitset = new BitSet();

    public PostOrderVisitor() {
    }

    @Override
    public boolean enter(int node) {
        try {
            return !bitset.get(node);
        } finally {
            bitset.set(node);
        }
    }

    public void reset() {
        bitset.clear();
    }
}
