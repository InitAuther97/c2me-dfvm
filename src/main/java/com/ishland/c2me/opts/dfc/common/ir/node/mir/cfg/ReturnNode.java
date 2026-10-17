package com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.ControlLike;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.ControlType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

/// Exit point of a single program. A ReturnNode post-dominates its
/// respective {@link FunctionBeginNode} (traversing down from the entry
/// always reaches exactly one ReturnNode) and points to {@link ProgramNode#END}.
/// Part of the control spine: a ReturnNode must never be eliminated
/// (see Canonicalizer).
public enum ReturnNode implements EndBlockNode {

    RET;

    @Override
    public IrType initialType() {
        return ControlType.CONTROL;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        return ControlLike.getControl(graph.getType(IrNode.control(graph, id)));
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        return false;
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }
}
