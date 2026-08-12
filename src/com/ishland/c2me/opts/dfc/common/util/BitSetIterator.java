package com.ishland.c2me.opts.dfc.common.util;

import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.ints.IntIterator;

import java.util.BitSet;
import java.util.NoSuchElementException;

public class BitSetIterator implements IntIterator {

    private final BitSet bitset;
    private int current;

    public BitSetIterator(BitSet bitset) {
        this.bitset = bitset;
        current = bitset.nextSetBit(0);
    }

    @Override
    public int nextInt() {
        final int result = current;
        if (result < 0) throw new NoSuchElementException();
        current = bitset.nextSetBit(result + 1);
        return result;
    }

    @Override
    public boolean hasNext() {
        return current >= 0;
    }

    @Override
    public void forEachRemaining(IntConsumer action) {
        final var bitset = this.bitset;
        int now = current;
        do {
            action.accept(now);
        } while ((now = bitset.nextSetBit(now + 1)) >= 0);
        current = -1;
    }
}
