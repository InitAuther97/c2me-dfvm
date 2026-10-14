package com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.HIRNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.stamp.VolumeStamp;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.hir.StampType;

public enum VariableVolumeNode implements HIRNode, VolumeOp {
    VOLUME;

    @Override
    public void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {

    }

    @Override
    public IrType initialType() {
        return new StampType(VolumeStamp.all());
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        return null;
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
