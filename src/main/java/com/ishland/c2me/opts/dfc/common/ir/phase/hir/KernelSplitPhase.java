package com.ishland.c2me.opts.dfc.common.ir.phase.hir;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;

/// The first high tier pass, running before all other high tier passes: it
/// splits the graph at border (shape-changing) nodes into per-work-item
/// kernels, then combines their effects by eliminating unused kernel
/// arguments and propagating each elimination to the callers.
///
/// A border node is a shape-changing operation: evaluating it for a given
/// volume requires evaluating its input for another, possibly shrunk,
/// volume, or evaluating at a work-item (x, y, z) requires evaluating the
/// wrapped density function at another work-item (x', y', z'), which may
/// collapse. To schedule these optimally, the wrapped subgraph runs in batch,
/// filling an array that the border's per-lane logic loads on demand,
/// eliminating redundant computation. Splitting the graph at every border
/// node keeps each kernel a pure per-work-item computation, so the split
/// must happen before any other high tier pass observes the graph.
///
/// Combining falls out of dead argument elimination instead of matching
/// border patterns: a border that ignores an incoming coordinate (e.g. a
/// slice's fixed axis) leaves the corresponding kernel argument unused. The
/// argument is removed from the callee's signature and from every caller's
/// packed volume, and the removal cascades — an operand dropped from a
/// packed volume may leave a caller argument unused, propagating further up
/// the call DAG until a fixpoint. Nested slices therefore compose without
/// being matched: each slice's fixed axes kill its own incoming arguments,
/// and the pass-through arguments die at the next boundary, leaving exactly
/// the composed coordinates. Sharing falls out too: a callee's elimination
/// is applied to all its call sites, and a caller argument survives only
/// while some callee still takes it.
///
/// The split is not implemented yet: it needs HIR program construction,
/// which does not exist. Collapses at the arithmetic level (e.g. an
/// interpolator interpolating between equal values along an axis whose
/// coordinate was eliminated) are not argument-level and need ordinary value
/// simplification instead.
public class KernelSplitPhase extends HIRCanonicalizationPhase {

    private final GraphContext ctx;

    public KernelSplitPhase(GraphContext ctx) {
        super(ctx);
        this.ctx = ctx;
    }

    @Override
    public void run() {
        // TODO: split the graph at border nodes into kernels, then eliminate
        //       unused kernel arguments to a fixpoint, propagating each
        //       elimination through the packed volumes to all callers
    }
}