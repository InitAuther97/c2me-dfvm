package com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.ControlType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

/// Entry point of a single program: takes a memory edge from its
/// {@link FunctionNode} as its first memory operand, marking the function it
/// is the control flow source of. {@link ProgramNode#START} points to the
/// FunctionBeginNode of every executable function; traversing down from one
/// always ends up in a single {@link ReturnNode} that post-dominates it.
/// Part of the control spine: a FunctionBeginNode must never be eliminated
/// (see Canonicalizer).
public enum FunctionBeginNode implements BeginBlockNode {

    BEGIN;

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
    public void export(IrGraph graph, int id, GraphBuilder builder) {
    }
}