package com.ishland.c2me.opts.dfc.common.ir.phase.hir;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.graph.value.NodeState;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.spline.SplineEntry;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.spline.SplineNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant.FloatConstantNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.HighTierPhase;
import com.ishland.flowsched.util.Assertions;

/// This pass specializes splines into shape-verified node variants, strongest
/// first:
/// - {@link SplineNode#UNIFORM_TABLE} for constant tables whose derivatives
///   are all zero, whose locations have a bit-uniform positive stride
///   {@code step = fl(location[1] - location[0])} and whose literals satisfy
///   {@code fl(location[0] + fl(i * step)) == location[i]} bitwise;
/// - {@link SplineNode#CONSTANT_TABLE} for splines whose entry values are all
///   f32 constants.
/// Non-matching splines are left untouched and already-specialized nodes are
/// never re-examined. Run before SplineExtensionPhase so uniform tables are
/// locked in before any padding is applied.
public class SplineSpecializePhase implements HighTierPhase {

    private final GraphContext ctx;

    public SplineSpecializePhase(GraphContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void run() {
        final IrGraph graph = ctx.getIrGraph();
        final int size = graph.nodes().size();
        for (int id = 0; id < size; id++) {
            final var state = graph.get(id);
            if (state == null || state.node() != SplineNode.SPLINE) {
                continue;
            }
            final SplineNode specialized = classify(graph, id);
            if (specialized == null) {
                continue;
            }
            graph.replaceNode(id, new NodeState(specialized, state.type()));
        }
    }

    /// Returns the strongest applicable specialization of the spline at the
    /// given id, or null if it is an unconstrained spline.
    private static SplineNode classify(IrGraph graph, int id) {
        final var value = graph.getValue();
        final var operands = value.getOperands(id);
        final int count = operands.size() - 1;
        if (count < 2) {
            return null; // coordinate and at least two entries
        }
        final float[] locations = new float[count];
        boolean allDerivativesZero = true;
        for (int i = 0; i < count; i++) {
            if (!(graph.getNode(operands.getInt(i + 1)) instanceof SplineEntry entry)) {
                return null;
            }
            final var entryOperands = value.getOperands(operands.getInt(i + 1));
            Assertions.assertTrue(entryOperands.size() == 1, "Spline entry must have exactly one value operand");
            if (!(graph.getNode(entryOperands.getInt(0)) instanceof FloatConstantNode)) {
                return null; // not a constant table
            }
            allDerivativesZero &= entry.derivative() == 0.0f;
            locations[i] = entry.location();
        }
        if (allDerivativesZero && isUniform(locations)) {
            return SplineNode.UNIFORM_TABLE;
        }
        return SplineNode.CONSTANT_TABLE;
    }

    private static boolean isUniform(float[] locations) {
        final float step = locations[1] - locations[0];
        if (!(step > 0.0f)) {
            return false;
        }
        for (int i = 1; i < locations.length; i++) {
            if (Float.floatToIntBits(locations[i] - locations[i - 1]) != Float.floatToIntBits(step)
                    || Float.floatToIntBits(locations[0] + i * step) != Float.floatToIntBits(locations[i])) {
                return false;
            }
        }
        return true;
    }
}
