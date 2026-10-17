package com.ishland.c2me.opts.dfc.common.ir.stamp;

public interface Stamp {
    default Stamp merge(Stamp other) {
        throw new UnsupportedOperationException("Stamp " + getClass() + " cannot be merged");
    }

    static boolean isCompatible(Stamp a, Stamp b) {
        return a.getClass() == b.getClass();
    }
}
