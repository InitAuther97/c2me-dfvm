package com.ishland.c2me.opts.dfc.common.ir.graph;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;

import java.util.NoSuchElementException;

public class RPOIterator implements IntIterator {

    final IntArrayList postOrder = new IntArrayList();
    int index;

    public RPOIterator(EdgeAccessor edges, int begin, int end) {
        PostOrderVisitor.populatePostOrder(edges, begin, end, this.postOrder);
        this.index = this.postOrder.size();
    }

    @Override
    public int nextInt() {
        final int idx = --this.index;
        if (idx < 0) throw new NoSuchElementException();
        return this.postOrder.getInt(idx);
    }

    @Override
    public boolean hasNext() {
        return this.index > 0;
    }
}
