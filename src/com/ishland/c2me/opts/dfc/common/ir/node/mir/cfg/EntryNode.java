package com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.ControlType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;

/// Entry point of a single program. {@link ProgramNode#START} points to at
/// least one EntryNode; traversing down from an EntryNode always ends up in
/// a single {@link ReturnNode} that post-dominates it. Part of the control
/// spine: an EntryNode must never be eliminated (see Canonicalizer).
public record EntryNode(String name) implements BeginBlockNode {

    @Override
    public IrType initialType() {
        return ControlType.CONTROL;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        return null;
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        return false;
    }

    @Override
    public int hash() {
        return name.hashCode();
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {
    }
}
