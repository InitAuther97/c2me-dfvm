package com.ishland.c2me.opts.dfc.common.ir.phase.hir;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.graph.value.GraphUtil;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.spline.SplineEntry;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.spline.SplineNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.constant.FloatConstantNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.AddFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.MulFPNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.operator.SubFPNode;
import com.ishland.flowsched.util.Assertions;
import it.unimi.dsi.fastutil.ints.IntList;

/// This pass recognizes Splines with only one defined point
/// and convert them to normal linear extension.
/// The emitted expression is `value + derivative * (x - location)` in f32,
/// exactly the formula used at runtime for out-of-range inputs, so the
/// replacement is bit-identical to the spline it replaces.
/// It also recognizes two-point splines whose points share one y-value and
/// have zero derivatives: every input evaluates to that y-value, including
/// out-of-range inputs, which short-circuit on a zero derivative, so the
/// spline is replaced by the shared value directly. This is bit-identical
/// up to the sign of zero, as the in-range evaluation normalizes -0.0.
public class SplineSimplifyPhase extends HIRCanonicalizationPhase {

    private final GraphContext ctx;

    public SplineSimplifyPhase(GraphContext ctx) {
        super(ctx);
        this.ctx = ctx;
    }

    @Override
    public void run() {
        final IrGraph graph = ctx.getIrGraph();
        final var value = graph.getValue();
        final int size = graph.nodes().size();
        for (int id = 0; id < size; id++) {
            if (graph.get(id) == null || !(graph.getNode(id) instanceof SplineNode)) {
                continue;
            }
            final var operands = value.getOperands(id);
            if (operands.size() == 2) {
                rewriteSinglePoint(graph, id, operands);
            } else if (operands.size() == 3) {
                rewriteSameValuePair(graph, id, operands);
            }
        }
        canonicalize();
    }

    /// Coordinate followed by a single entry: the spline is the linear
    /// extension `value + derivative * (x - location)`.
    private void rewriteSinglePoint(IrGraph graph, int id, IntList operands) {
        final var value = graph.getValue();
        if (!(graph.getNode(operands.getInt(1)) instanceof SplineEntry entry)) {
            return;
        }
        final int coordinate = operands.getInt(0);
        final int yValue = singleValueOperand(graph, operands.getInt(1));

        final int location = graph.addNode(new FloatConstantNode(entry.location()));
        final int sub = graph.addNode(SubFPNode.SUB_F);
        value.appendEdge(coordinate, sub);
        value.appendEdge(location, sub);

        final int derivative = graph.addNode(new FloatConstantNode(entry.derivative()));
        final int mul = graph.addNode(MulFPNode.MUL_F);
        value.appendEdge(sub, mul);
        value.appendEdge(derivative, mul);

        final int add = graph.addNode(AddFPNode.ADD_F);
        value.appendEdge(yValue, add);
        value.appendEdge(mul, add);

        enqueue(location);
        enqueue(sub);
        enqueue(derivative);
        enqueue(mul);
        enqueue(add);

        transfer(graph, id, add);
    }

    /// Coordinate followed by two entries sharing one y-value with zero
    /// derivatives: the spline evaluates to that value for every input.
    private void rewriteSameValuePair(IrGraph graph, int id, IntList operands) {
        if (!(graph.getNode(operands.getInt(1)) instanceof SplineEntry first)) {
            return;
        }
        if (!(graph.getNode(operands.getInt(2)) instanceof SplineEntry second)) {
            return;
        }
        if (first.derivative() != 0.0f || second.derivative() != 0.0f) {
            return;
        }
        final int yValue = singleValueOperand(graph, operands.getInt(1));
        if (!sameValue(graph, yValue, singleValueOperand(graph, operands.getInt(2)))) {
            return;
        }
        transfer(graph, id, yValue);
    }

    private void transfer(IrGraph graph, int id, int replacement) {
        final var value = graph.getValue();
        final var usages = value.getUsages(id);
        while (!usages.isEmpty()) {
            final int downstream = GraphUtil.getLast(usages);
            enqueue(downstream);
            value.replaceEdge(downstream, id, replacement);
        }
        enqueue(id); // now dead, let the canonicalizer collect it
    }

    private static boolean sameValue(IrGraph graph, int a, int b) {
        if (a == b) {
            return true;
        }
        return graph.getNode(a) instanceof FloatConstantNode first
                && graph.getNode(b) instanceof FloatConstantNode second
                && Float.floatToIntBits(first.constant()) == Float.floatToIntBits(second.constant());
    }

    private static int singleValueOperand(IrGraph graph, int entry) {
        final var operands = graph.getValue().getOperands(entry);
        Assertions.assertTrue(operands.size() == 1, "Spline entry must have exactly one value operand");
        return operands.getInt(0);
    }
}