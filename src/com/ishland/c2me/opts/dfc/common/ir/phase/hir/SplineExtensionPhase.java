package com.ishland.c2me.opts.dfc.common.ir.phase.hir;

import com.ishland.c2me.opts.dfc.common.ir.phase.HighTierPhase;

/// This pass analyzes spline nodes and does proper extending or merging
/// to spline's sample points, so all inputs fall into the definition range.
/// This pass checks whether the spline ends with points with derivative==0.0f.
/// If not, bail out early for the specific node. Then, add two points close to
/// the border of the input range, with the same value as the endpoints. This
/// converts the spline node to a "safe" state, where an overflow may never
/// happen.
public class SplineExtensionPhase implements HighTierPhase {
    @Override
    public void run() {

    }
}
