package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.proxy;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.ValueOpNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

import java.util.Objects;

public record CastNode(IrType target) implements ProxyNode {

    @Override
    public IrType initialType() {
        return target;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final int operand = IrNode.data(graph, id, 0);
        return ValueOpNode.verifyValue(graph.getType(operand).join(target));
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        final var operands = graph.getValue().getOperands(id);
        final int input = operands.getInt(1);
        final var typeIn = graph.getType(input);
        if (!target.is(typeIn)) {
            return false;
        }
        IrNode.transferDownstream(graph.getValue(), id, input, canonicalizer);
        return true;
    }

    @Override
    public boolean isEqual(IrNode that) {
        return Objects.equals(this.target, ((CastNode) that).target);
    }

    @Override
    public int hash() {
        return target.hashCode();
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }

    @Override
    public int getCost() {
        return 0;
    }
}
