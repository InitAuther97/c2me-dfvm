package com.ishland.c2me.opts.dfc.common.ir.test;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelArgumentNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.group.GroupNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.volume.DensityVolumeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel.KernelRootNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.value.LerpNode;
import com.ishland.c2me.opts.dfc.common.ir.test.framework.Assert;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.FloatType;

/// Fixed expected bounds from vanilla Interval.lerp, tested through both HIR nodes.
/// These cases exercise range inference, independently of sampling or optimization.
@SuppressWarnings("unused")
public class LerpTypeComputationTest {

    public void testConstantInterpolation() {
        assertLerpRange(FloatType.constant(4.0f), FloatType.constant(0.25f),
                FloatType.constant(2.0f), FloatType.constant(10.0f));
    }

    public void testIndependentUnitRangesStayBounded() {
        final FloatType unit = FloatType.range(0.0f, 1.0f);
        // Independently expanding start + alpha * (end - start) gives [-1, 2].
        assertLerpRange(unit, unit, unit, unit);
    }

    public void testDisjointEndpointRanges() {
        assertLerpRange(FloatType.range(4.0f, 16.0f), FloatType.range(0.25f, 0.75f),
                FloatType.range(2.0f, 4.0f), FloatType.range(10.0f, 20.0f));
    }

    public void testReversedEndpointRanges() {
        assertLerpRange(FloatType.range(4.0f, 16.0f), FloatType.range(0.25f, 0.75f),
                FloatType.range(10.0f, 20.0f), FloatType.range(2.0f, 4.0f));
    }

    public void testExtrapolationUsesMixedEndpointPairs() {
        // Both extrema use start=2 and end=20, rather than matching min/min or max/max.
        assertLerpRange(FloatType.range(-16.0f, 38.0f), FloatType.range(-1.0f, 2.0f),
                FloatType.range(2.0f, 4.0f), FloatType.range(10.0f, 20.0f));
    }

    public void testUnboundedAlphaWithFiniteEndpoints() {
        // Interval multiplication treats infinity times a zero difference as zero.
        assertLerpRange(FloatType.constant(3.0f), FloatType.ALL,
                FloatType.constant(3.0f), FloatType.constant(3.0f));
        assertLerpRange(FloatType.ALL, FloatType.ALL,
                FloatType.constant(2.0f), FloatType.constant(10.0f));
    }

    public void testFiniteOverflowRetainsInfiniteBounds() {
        final FloatType alpha = FloatType.range(0.0f, 1.0f);
        final FloatType positive = FloatType.constant(Float.MAX_VALUE);
        final FloatType negative = FloatType.constant(-Float.MAX_VALUE);
        assertLerpRange(FloatType.range(Float.NEGATIVE_INFINITY, Float.MAX_VALUE), alpha, positive, negative);
        assertLerpRange(FloatType.range(-Float.MAX_VALUE, Float.POSITIVE_INFINITY), alpha, negative, positive);
    }

    public void testZeroAlphaWithInfiniteEnd() {
        for (float infinity : new float[]{Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY}) {
            assertLerpRange(FloatType.constant(2.0f), FloatType.constant(0.0f),
                    FloatType.constant(2.0f), FloatType.constant(infinity));
        }
    }

    public void testOneAlphaWithInfiniteStart() {
        for (float infinity : new float[]{Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY}) {
            assertLerpRange(FloatType.constant(2.0f), FloatType.constant(1.0f),
                    FloatType.constant(infinity), FloatType.constant(2.0f));
        }
    }

    public void testEqualInfiniteEndpoints() {
        for (float infinity : new float[]{Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY}) {
            final FloatType endpoint = FloatType.constant(infinity);
            assertLerpRange(endpoint, FloatType.ALL, endpoint, endpoint);
        }
    }

    public void testInfiniteEndpointExtrapolation() {
        final FloatType negative = FloatType.constant(Float.NEGATIVE_INFINITY);
        final FloatType positive = FloatType.constant(Float.POSITIVE_INFINITY);
        assertLerpRange(negative, FloatType.range(-1.0f, 0.0f), negative, positive);
        assertLerpRange(positive, FloatType.range(1.0f, 2.0f), negative, positive);
        assertLerpRange(positive, FloatType.range(-1.0f, 0.0f), positive, negative);
        assertLerpRange(negative, FloatType.range(1.0f, 2.0f), positive, negative);
    }

    public void testNaNOnlyCornersAreSkipped() {
        // The (+infinity, -infinity) corner is NaI, but (+infinity, 2) remains valid.
        assertLerpRange(FloatType.constant(Float.POSITIVE_INFINITY), FloatType.constant(0.5f),
                FloatType.constant(Float.POSITIVE_INFINITY), FloatType.range(Float.NEGATIVE_INFINITY, 2.0f));
    }

    public void testEntirelyNaNResultWidensToAll() {
        assertLerpRange(FloatType.ALL, FloatType.range(0.25f, 0.75f),
                FloatType.constant(Float.NEGATIVE_INFINITY), FloatType.constant(Float.POSITIVE_INFINITY));
    }

    public void testInfiniteEndpointPairRejectsEitherNaNBound() {
        final FloatType start = FloatType.constant(Float.NEGATIVE_INFINITY);
        final FloatType end = FloatType.constant(Float.POSITIVE_INFINITY);
        // Infinite endpoints reject the entire corner when either alpha bound gives NaN.
        assertLerpRange(FloatType.ALL, FloatType.range(0.0f, 0.5f), start, end);
        assertLerpRange(FloatType.ALL, FloatType.range(0.5f, 1.0f), start, end);
    }

    public void testSignedZeroBoundsFollowIntervalConvention() {
        final FloatType alpha = FloatType.range(-1.0f, 1.0f);
        final FloatType negative = FloatType.constant(-0.0f);
        final FloatType positive = FloatType.constant(0.0f);
        // Vanilla's range-only zero product normalizes these bounds to positive zero.
        assertLerpRange(positive, alpha, negative, negative);
        assertLerpRange(positive, alpha, negative, positive);
        assertLerpRange(positive, alpha, positive, negative);
    }

    public void testUndeterminedInputYieldsTop() {
        final FloatType known = FloatType.range(1.0f, 2.0f);
        for (FloatType unknown : new FloatType[]{FloatType.TOP, FloatType.range(2.0f, 1.0f)}) {
            assertLerpRange(FloatType.TOP, unknown, known, known);
            assertLerpRange(FloatType.TOP, known, unknown, known);
            assertLerpRange(FloatType.TOP, known, known, unknown);
        }
    }

    private static void assertLerpRange(FloatType expected, FloatType alpha, FloatType start, FloatType end) {
        for (LerpNode node : LerpNode.values()) {
            final FloatType actual = computeType(node, alpha, start, end);
            final String message = node + " alpha=[" + alpha.min() + ", " + alpha.max()
                    + "] start=[" + start.min() + ", " + start.max()
                    + "] end=[" + end.min() + ", " + end.max() + "]";
            // FloatType.equals does not distinguish zero signs; scalar comparisons do.
            Assert.assertEquals(expected.min(), actual.min(), message + " minimum");
            Assert.assertEquals(expected.max(), actual.max(), message + " maximum");
            if (expected == FloatType.TOP) Assert.assertSame(FloatType.TOP, actual, message);
        }
    }

    private static FloatType computeType(LerpNode node, FloatType alpha, FloatType start, FloatType end) {
        final GraphContext ctx = new GraphContext();
        final IrGraph graph = ctx.getIrGraph();
        final int group = graph.addNode(GroupNode.TAG);
        final int volume = graph.addNode(DensityVolumeNode.UNKNOWN);
        graph.getMemory().appendEdge(group, volume);
        final int descriptor = graph.addNode(new KernelNode("lerpType"));
        graph.getMemory().appendEdge(volume, descriptor);
        final int lerp = graph.addNode(node);
        final FloatType[] types = {alpha, start, end};
        for (int i = 0; i < types.length; i++) {
            final int argument = graph.addNode(new KernelArgumentNode("arg" + i, types[i]));
            graph.getMemory().appendEdge(descriptor, argument);
            graph.getValue().appendEdge(argument, lerp);
        }
        final int root = graph.addNode(KernelRootNode.ROOT);
        graph.getMemory().appendEdge(descriptor, root);
        graph.getValue().appendEdge(lerp, root);
        graph.getControl().appendEdge(ctx.getProgramStart(), root);
        graph.getControl().appendEdge(root, ctx.getProgramEnd());
        return (FloatType) node.compute(graph, lerp, ctx);
    }
}
