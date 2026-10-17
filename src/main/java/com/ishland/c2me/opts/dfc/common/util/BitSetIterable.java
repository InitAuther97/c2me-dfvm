package com.ishland.c2me.opts.dfc.common.util;

import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntIterator;

import java.util.BitSet;

public record BitSetIterable(BitSet bitset) implements IntIterable {
    @Override
    public IntIterator iterator() {
        return new BitSetIterator(bitset);
    }
}
