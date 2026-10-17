package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.ValueOpNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.DoubleType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.FloatType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.DoubleUtil;
import com.ishland.c2me.opts.dfc.common.ir.type.util.FloatUtil;

public enum DivFPNode implements ValueOpNode, FPBinaryOp {
    DIV_F, DIV_D;

    @Override
    public IrType initialType() {
        return this == DIV_D ? DoubleType.TOP : FloatType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operands = graph.getValue().getOperands(id);
        final var first = graph.getType(operands.getInt(0));
        final var second = graph.getType(operands.getInt(1));
        if (this == DIV_D) {
            return DoubleUtil.div((DoubleType) first, (DoubleType) second);
        } else {
            return FloatUtil.div((FloatType) first, (FloatType) second);
        }
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        return false;
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }

    @Override
    public int getCost() {
        return 0;
    }

    @Override
    public float evaluate32(float left, float right) {
        return left / right;
    }

    @Override
    public double evaluate64(double left, double right) {
        return left / right;
    }
}