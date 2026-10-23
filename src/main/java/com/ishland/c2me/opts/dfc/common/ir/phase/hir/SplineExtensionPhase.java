package com.ishland.c2me.opts.dfc.common.ir.phase.hir;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.spline.SplineEntry;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.spline.SplineNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.HighTierPhase;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.FloatType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.TypeUtil;
import com.ishland.flowsched.util.Assertions;

/// This pass analyzes spline nodes and does proper extending or merging
/// to spline's sample points, so all inputs fall into the definition range.
/// This pass checks whether the spline ends with points with derivative==0.0f.
/// If not, bail out early for the specific node. Then, add two points close to
/// the border of the input range, with the same value as the endpoints. This
/// converts the spline node to a "safe" state, where an overflow may never
/// happen.
/// The input range is read from the computed type of the coordinate operand;
/// if it is unknown, the spline is left untouched. Because the added points
/// carry zero derivative and the same y-value as the endpoints, evaluating
/// them is bit-identical to the linear extension they replace. Each side is
/// handled independently; an endpoint with a nonzero derivative is left as-is.
public class SplineExtensionPhase implements HighTierPhase {

    private final GraphContext ctx;

    public SplineExtensionPhase(GraphContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void run() {
        final IrGraph graph = ctx.getIrGraph();
        final var value = graph.getValue();
        final int size = graph.nodes().size();
        for (int id = 0; id < size; id++) {
            if (graph.get(id) == null || !(graph.getNode(id) instanceof SplineNode spline)) {
                continue;
            }
            if (spline == SplineNode.UNIFORM_TABLE) {
                continue; // uniform tables keep their stride contract: out-of-range inputs already take the endpoint value
            }
            final var operands = value.getOperands(id);
            if (operands.size() < 3) {
                continue; // coordinate and at least two entries; single-point splines belong to SplineSimplifyPhase
            }
            final int coordinate = operands.getInt(0);
            final int firstId = operands.getInt(1);
            final int lastId = operands.getInt(operands.size() - 1);
            if (!(graph.getNode(firstId) instanceof SplineEntry(float derivative, float location))) {
                continue;
            }
            if (!(graph.getNode(lastId) instanceof SplineEntry(float derivative1, float location1))) {
                continue;
            }
            final IrType coordinateType = TypeUtil.getValueType(graph, coordinate);
            if (!(coordinateType instanceof FloatType range) || !range.isDetermined()) {
                continue; // input range unknown, cannot extend
            }
            final float min = range.min(), max = range.max();
            if (!Float.isFinite(min) || !Float.isFinite(max)) {
                continue;
            }

            if (derivative == 0.0f && min < location) {
                final int phantom = graph.addNode(new SplineEntry(0.0f, min));
                value.appendEdge(singleValueOperand(graph, firstId), phantom);
                value.insertEdge(id, 1, phantom);
            }
            if (derivative1 == 0.0f && max > location1) {
                final int phantom = graph.addNode(new SplineEntry(0.0f, max));
                value.appendEdge(singleValueOperand(graph, lastId), phantom);
                value.appendEdge(phantom, id);
            }
        }
    }

    private static int singleValueOperand(IrGraph graph, int entry) {
        final var operands = graph.getValue().getOperands(entry);
        Assertions.assertTrue(operands.size() == 1, "Spline entry must have exactly one value operand");
        return operands.getInt(0);
    }
}
