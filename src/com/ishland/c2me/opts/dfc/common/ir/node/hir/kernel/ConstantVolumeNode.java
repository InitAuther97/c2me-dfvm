package com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.stamp.CoordinationStamp;
import com.ishland.c2me.opts.dfc.common.ir.stamp.VolumeStamp;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.hir.StampType;

import java.util.Objects;

public record ConstantVolumeNode(
        int sizeX, int sizeY, int sizeZ,
        int stepX, int stepY, int stepZ
) implements HIRNode {

    @Override
    public void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {
    }

    @Override
    public IrType initialType() {
        return StampType.create(VolumeStamp.create(
                stepX, stepY, stepZ,
                sizeX, sizeY, sizeZ,
                CoordinationStamp.TOP
        ));
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var stamp = (VolumeStamp) ((StampType) graph.getType(id)).getStamp();
        final var origin = (CoordinationStamp) ((StampType) graph.getType(IrNode.data(graph, id, 0))).getStamp();
        if (Objects.equals(stamp.origin(), origin)) {
            return null;
        }
        return StampType.create(VolumeStamp.create(stepX, stepY, stepZ, sizeX, sizeY, sizeZ, origin));
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        return false;
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {

    }

    @Override
    public int getCost() {
        return 0;
    }
}
