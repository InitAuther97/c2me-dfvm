package com.ishland.c2me.opts.dfc.common.ir.phase.cfg;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.PostRunCanonicalizerPhase;

/// This phase performs loop rotation on all loops that need a rotation.
///
/// A loop that needs a rotation is defined as one that has an exit check
/// in the end of its header block and a basic block on its back edge.
///
/// Due to the lazy nature of late scheduling, values flowing back to the
/// phi are always scheduled on the back edge, to defer computations.
/// Redundant computations on back edge are not discovered by GVNPRE since
/// the loop can run this block for zero times, violating optimality.
///
/// We resolve this by guarding that the loop run for at least once. We
/// "rotate" the loop, peeling the exit check out as a guard, and insert it
/// at the last on the back edge, ensuring the original block is executed
/// for at least once, therefore enabling LICM for GVNPRE. We also need to
/// insert a Merge block because the original destination for exit block
/// now has 2 incoming edges, 1 from guard and 1 from the new exit block.
///
/// A case is that the computation is also used in loop check, which makes
/// the node scheduled earlier in the loop, leaving back edge a no-op.
/// In this case, rotation is not needed.
///
/// Since rotation inserts new nodes and control flows, we need to run
/// canonicalizer to find crucial optimization, such as constant condition
/// for a control flow, enabling the elimination of the loop guard. This is
/// usually observed on counted loops, whose rotation is theoretically free.
public class LoopRotation extends PostRunCanonicalizerPhase implements SchedulePhase {
    public LoopRotation(GraphContext ctx) {
        super(ctx);
    }

    @Override
    public void run() {
        // We can't use collect and apply scheduling here as we modify CFG.

    }
}
