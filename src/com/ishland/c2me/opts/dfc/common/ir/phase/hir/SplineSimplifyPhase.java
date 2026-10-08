package com.ishland.c2me.opts.dfc.common.ir.phase.hir;

import com.ishland.c2me.opts.dfc.common.ir.phase.HighTierPhase;

/// This pass recognizes Splines with only one defined point
/// and convert them to normal linear extension.
public class SplineSimplifyPhase implements HighTierPhase {
    @Override
    public void run() {

    }
}
