package com.ishland.c2me.opts.dfc.common.ir.type.util;

import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.MemoryValueType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.MemoryType;

public final class TypeUtil {

    private TypeUtil() {
    }

    /// Read a stored type and extract its value component, if compound.
    public static IrType getValueType(IrGraph graph, int id) {
        final var type = graph.getType(id);
        return type instanceof MemoryValueType mv ? mv.getValueType() : type;
    }

    /// Read a stored type and extract its memory component, preserving marker types.
    public static MemoryType getMemoryType(IrGraph graph, int id) {
        final var type = graph.getType(id);
        return type instanceof MemoryValueType mv ? mv.getMemoryType() : (MemoryType) type;
    }
}
