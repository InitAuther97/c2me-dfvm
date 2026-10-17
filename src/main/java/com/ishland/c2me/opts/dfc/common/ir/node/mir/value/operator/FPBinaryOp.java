package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator;

public interface FPBinaryOp extends FPCommonOp {
    float evaluate32(float left, float right);
    double evaluate64(double left, double right);
}