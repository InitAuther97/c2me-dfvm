package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.ValueOpNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.DoubleType;
import com.ishland.c2me.opts.dfc.common.ir.type.FloatType;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.DoubleUtil;
import com.ishland.c2me.opts.dfc.common.ir.type.util.FloatUtil;

public enum SqrtFPNode implements ValueOpNode, FPUnaryOp {
    SQRT_F {
        @Override
        public float evaluate32(float input) {
            return (float) Math.sqrt(input);
        }
    },
    SQRT_D {
        @Override
        public double evaluate64(double input) {
            return Math.sqrt(input);
        }
    };

    @Override
    public boolean isSame(IrNode node) {
        return node == this;
    }

    @Override
    public IrType initialType() {
        return this == SQRT_D ? DoubleType.TOP : FloatType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operands = graph.getValue().getOperands(id);
        final var input = graph.getType(operands.getInt(0));
        if (this == SQRT_D) {
            return DoubleUtil.sqrt((DoubleType) input);
        } else {
            return FloatUtil.sqrt((FloatType) input);
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
}