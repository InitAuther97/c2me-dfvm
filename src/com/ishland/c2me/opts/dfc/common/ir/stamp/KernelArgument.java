package com.ishland.c2me.opts.dfc.common.ir.stamp;

/// Identifies one of the 9 i32 arguments that describe a kernel's input
/// volume: sampling step, sample count and origin per axis.
public enum KernelArgument implements Stamp {
    STEP_X, STEP_Y, STEP_Z,
    SIZE_X, SIZE_Y, SIZE_Z,
    ORIGIN_X, ORIGIN_Y, ORIGIN_Z;
}