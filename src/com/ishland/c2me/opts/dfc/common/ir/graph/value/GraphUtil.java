package com.ishland.c2me.opts.dfc.common.ir.graph.value;

import it.unimi.dsi.fastutil.ints.IntList;

public class GraphUtil {
    public static int getLast(IntList list) {
        return list.getInt(list.size() - 1);
    }
}
