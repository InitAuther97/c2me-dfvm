package com.ishland.c2me.opts.dfc.common.ir.node.hir.value;

import com.ishland.c2me.opts.dfc.common.ir.CompileFeature;
import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.graph.value.NodeState;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.FloatType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.FloatUtil;
import com.ishland.c2me.opts.dfc.common.ir.type.util.TypeUtil;

/// Linearly interpolates between two f32 values: value operands are
/// `[alpha, start, end]` and the result is
/// `start + alpha * (end - start)`.
///
/// {@link #LERP_EXACT} additionally selects `end` exactly when
/// `alpha == 1.0F`, matching the fast path of vanilla's
/// `LerpDensityFunction` sampler. It is demoted to {@link #LERP} when alpha's
/// type range excludes `1.0F`, or when
/// {@link CompileFeature#UNSAFE_MATH} is enabled.
public enum LerpNode implements HIRNode {

    LERP(false),
    LERP_EXACT(true);

    /// Whether `alpha == 1.0F` selects the second value exactly.
    private final boolean exact;

    LerpNode(boolean exact) {
        this.exact = exact;
    }

    public boolean isExact() {
        return exact;
    }

    @Override
    public void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {
        // TODO: LERP expands into SubFPNode / MulFPNode / AddFPNode;
        //       LERP_EXACT additionally guards alpha == 1.0F and selects end
    }

    @Override
    public IrType initialType() {
        return FloatType.TOP;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var alpha = (FloatType) TypeUtil.getValueType(graph, IrNode.data(graph, id, 0));
        final var start = (FloatType) TypeUtil.getValueType(graph, IrNode.data(graph, id, 1));
        final var end = (FloatType) TypeUtil.getValueType(graph, IrNode.data(graph, id, 2));
        return FloatUtil.add(start, FloatUtil.mul(alpha, FloatUtil.sub(end, start)));
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        if (!exact) {
            return false;
        }
        if (!ctx.isFeatureEnabled(CompileFeature.UNSAFE_MATH)
                && mayBeOne(TypeUtil.getValueType(graph, IrNode.data(graph, id, 0)))) {
            return false;
        }
        graph.replaceNode(id, new NodeState(LERP, graph.get(id).type()));
        return true;
    }

    /// Whether the alpha range cannot rule out `1.0F`.
    private static boolean mayBeOne(IrType type) {
        if (!(type instanceof FloatType alpha) || !alpha.isDetermined()) {
            return true;
        }
        return alpha.min() <= 1.0f && alpha.max() >= 1.0f;
    }

    @Override
    public boolean isEqual(IrNode that) {
        return that == this;
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {
    }

    @Override
    public int getCost() {
        return 3;
    }
}
