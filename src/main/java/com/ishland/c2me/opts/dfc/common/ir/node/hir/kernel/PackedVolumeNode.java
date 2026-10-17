package com.ishland.c2me.opts.dfc.common.ir.node.hir.kernel;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.export.GraphBuilder;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.StampNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;
import com.ishland.c2me.opts.dfc.common.ir.stamp.VolumeStamp;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.hir.StampType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.IntegerType;
import com.ishland.flowsched.util.Assertions;

/// Marker node combining the nine volume arguments of a kernel (in
/// {@link VolumeStamp} record order, `sizeX` .. `stepBlockZ`) into the packed
/// volume fed into a kernel call as its input volume. Its stamp type carries
/// the i32 range of each argument.
public enum PackedVolumeNode implements StampNode {

    INSTANCE;

    @Override
    public void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer) {
    }

    @Override
    public IrType initialType() {
        return VolumeStamp.TOP_TYPE;
    }

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operands = graph.getValue().getOperands(id);
        Assertions.assertTrue(operands.size() == 9, "packed volume must have exactly 9 operands");
        return StampType.create(new VolumeStamp(
                (IntegerType) graph.getType(operands.getInt(0)),
                (IntegerType) graph.getType(operands.getInt(1)),
                (IntegerType) graph.getType(operands.getInt(2)),
                (IntegerType) graph.getType(operands.getInt(3)),
                (IntegerType) graph.getType(operands.getInt(4)),
                (IntegerType) graph.getType(operands.getInt(5)),
                (IntegerType) graph.getType(operands.getInt(6)),
                (IntegerType) graph.getType(operands.getInt(7)),
                (IntegerType) graph.getType(operands.getInt(8))
        ));
    }

    @Override
    public boolean optimize(IrGraph graph, int id, Canonicalizer canonicalizer, GraphContext ctx) {
        return false;
    }

    @Override
    public boolean isEqual(IrNode that) {
        return that instanceof PackedVolumeNode;
    }

    @Override
    public void export(IrGraph graph, int id, GraphBuilder builder) {
    }

    @Override
    public int getCost() {
        return 0;
    }
}