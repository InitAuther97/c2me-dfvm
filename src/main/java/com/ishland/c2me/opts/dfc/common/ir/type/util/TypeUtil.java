package com.ishland.c2me.opts.dfc.common.ir.type.util;

import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

public final class TypeUtil {

    private TypeUtil() {
    }

    /// Read a stored type and extract its value component, if compound.
    public static IrType getValueType(IrGraph graph, int id) {
        return graph.getType(id).getValueType();
    }

    /// Read a stored type and extract its memory component, preserving marker types.
    public static IrType getMemoryType(IrGraph graph, int id) {
        return graph.getType(id).getMemoryType();
    }
}
