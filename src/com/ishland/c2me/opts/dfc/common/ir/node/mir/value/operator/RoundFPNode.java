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
import com.ishland.flowsched.util.Assertions;

public enum RoundFPNode implements ValueOpNode, FPUnaryOp {
    FLOOR_F((byte) 0, true),
    CEIL_F((byte) 1, true),
    NEAREST_F((byte) 2, true),
    TRUNCATE_F((byte) 3, true),
    FLOOR_D((byte) 0, false),
    CEIL_D((byte) 1, false),
    NEAREST_D((byte) 2, false),
    TRUNCATE_D((byte) 3, false),;

    byte roundingMode;
    boolean isFloat;

    RoundFPNode(byte roundingMode, boolean isFloat) {
        this.roundingMode = roundingMode;
        this.isFloat = isFloat;
    }

    @Override
    public float evaluate32(float input) {
        return switch(roundingMode) {
            case 0 -> (float) Math.floor(input);
            case 1 -> (float) Math.ceil(input);
            case 2 -> (float) Math.rint(input);
            case 3 -> input < 0 ? (float) Math.ceil(input) : (float) Math.floor(input);
            default -> throw new IllegalStateException("Unexpected value: " + roundingMode);
        };
    }

    @Override
    public double evaluate64(double input) {
        return switch(roundingMode) {
            case 0 -> Math.floor(input);
            case 1 -> Math.ceil(input);
            case 2 -> Math.rint(input);
            case 3 -> input < 0 ? Math.ceil(input) : Math.floor(input);
            default -> throw new IllegalStateException("Unexpected value: " + roundingMode);
        };
    }

    @Override
    public IrType initialType() {
        return isFloat ? FloatType.TOP : DoubleType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operands = graph.getValue().getOperands(id);
        Assertions.assertTrue(operands.size() == 1, "Too many operands");
        final int operand = operands.getInt(0);
        final var in = graph.getType(operand);
        if (!in.isDetermined())
            return initialType();
        if (isFloat) {
            final var f32 = (FloatType) in;
            return FloatType.range(evaluate32(f32.min()), evaluate32(f32.max()));
        } else {
            final var f64 = (DoubleType) in;
            return DoubleType.range(evaluate64(f64.min()), evaluate64(f64.max()));
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
