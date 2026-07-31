package com.ishland.c2me.opts.dfc.common.util;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;

public class UnmodifiableFastCompareIntArrayList extends IntLists.UnmodifiableList {
    protected final FastCompareIntArrayList arrayList;
    public UnmodifiableFastCompareIntArrayList(FastCompareIntArrayList l) {
        super(l);
        this.arrayList = l;
    }

    @Override
    public int hashCode() {
        return arrayList.hashCode();
    }

    @SuppressWarnings("EqualsDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return arrayList.equals(o);
    }

    public static IntList unmodifiable(IntList l) {
        return l == IntLists.EMPTY_LIST ? l : l instanceof FastCompareIntArrayList inner ? new UnmodifiableFastCompareIntArrayList(inner) : IntLists.unmodifiable(l);
    }
}
