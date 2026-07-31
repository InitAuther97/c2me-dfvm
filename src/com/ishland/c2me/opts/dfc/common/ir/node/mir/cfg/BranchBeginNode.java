package com.ishland.c2me.opts.dfc.common.ir.node.mir.cfg;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.BranchType;
import com.ishland.c2me.opts.dfc.common.ir.type.ControlType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

public enum BranchBeginNode implements BeginBlockNode {
    TRUE((byte) 0b01), FALSE((byte) 0b10);

    private final byte bit;

    BranchBeginNode(byte bit) {
        this.bit = bit;
    }

    public byte getBit() {
        return bit;
    }

    @Override
    public IrType initialType() {
        return ControlType.CONTROL;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operand = graph.getOperands(id);
        final int node = operand.getInt(0);
        final var type = (BranchType) graph.getType(node);
        return (type.getBranch() & this.bit) == 0 ? ControlType.DEAD_CONTROL : ControlType.CONTROL;
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        // IfBegin is only a marker node, nothing we need to do
        // The update is propagated to If when we're eliminated
        return false;
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }
}
