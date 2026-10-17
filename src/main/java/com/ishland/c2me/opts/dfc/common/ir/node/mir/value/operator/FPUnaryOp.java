package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator;

public interface FPUnaryOp extends FPCommonOp {
    float evaluate32(float input);

    double evaluate64(double input);
}