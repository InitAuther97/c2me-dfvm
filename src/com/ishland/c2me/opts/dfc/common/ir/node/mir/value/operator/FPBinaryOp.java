package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator;

public interface FPBinaryOp extends FPCommonOp {
    default float evaluate32(float left, float right) {
        throw new UnsupportedOperationException();
    }

    default double evaluate64(double first, double second) {
        throw new UnsupportedOperationException();
    }
}