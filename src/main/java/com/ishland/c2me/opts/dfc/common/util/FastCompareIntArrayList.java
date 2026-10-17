package com.ishland.c2me.opts.dfc.common.util;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.Arrays;

public class FastCompareIntArrayList extends IntArrayList {

    @Override
    public boolean equals(IntArrayList l) {
        if (l == this) return true;
        int s = size();
        if (s != l.size()) return false;
        final int[] a1 = elements();
        final int[] a2 = l.elements();
        if (a1 == a2) return true;
        return Arrays.equals(a1, 0, s - 1, a2, 0, s - 1);
    }
}
