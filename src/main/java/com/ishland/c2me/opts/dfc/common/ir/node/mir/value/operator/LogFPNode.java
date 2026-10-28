package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.ValueOpNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.DoubleType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.FloatType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.TypeUtil;

/// Natural logarithm; f32 evaluation matches the vanilla LOG unary density sampler.
public enum LogFPNode implements ValueOpNode, FPUnaryOp {
    LOG_F, LOG_D;

    @Override
    public IrType initialType() {
        return this == LOG_D ? DoubleType.TOP : FloatType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operands = graph.getValue().getOperands(id);
        final var input = TypeUtil.getValueType(graph, operands.getInt(0));
        if (!input.isDetermined()) return initialType();
        if (this == LOG_D) {
            final var type = (DoubleType) input;
            if (type.min() < 0.0) return DoubleType.ALL; // NaN possible
            return DoubleType.range(evaluate64(type.min()), evaluate64(type.max()));
        } else {
            final var type = (FloatType) input;
            if (type.min() < 0.0f) return FloatType.ALL; // NaN possible
            return FloatType.range(evaluate32(type.min()), evaluate32(type.max()));
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
    public float evaluate32(float input) {
        return (float) Math.log(input);
    }

    @Override
    public double evaluate64(double input) {
        return Math.log(input);
    }
}
