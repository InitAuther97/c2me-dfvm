package com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.ControlType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

/// Boundary of the whole graph. A valid graph always contains exactly one
/// START and one END. In MIR, START points to executable
/// {@link FunctionBeginNode}s and every {@link ReturnNode} points to END.
/// In HIR, roots attach to both boundaries only for structural reachability.
/// Both boundaries must survive canonicalization.
public enum ProgramNode implements ControlNode {
    START, END;

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
