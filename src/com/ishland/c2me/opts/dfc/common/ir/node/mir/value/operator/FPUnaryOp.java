package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator;

public interface FPUnaryOp extends FPCommonOp {
    default float evaluate32(float input) {
        throw new UnsupportedOperationException();
    }

    default double evaluate64(double input) {
        throw new UnsupportedOperationException();
    }
}