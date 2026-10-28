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
import com.ishland.c2me.opts.dfc.common.ir.type.util.TypeUtil;
import com.ishland.flowsched.util.Assertions;

/// Metadata of a kernel invocation: records the volume of work items actually
/// sampled for the given volume of inputs. It is attached to a
/// {@link KernelInvokeNode} as its second memory operand, and its stamp type
/// carries the i32 range of each of the nine volume fields (in
/// {@link VolumeStamp} record order, `sizeX` .. `stepBlockZ`).
///
/// The node never takes part in a per-work-item kernel expression: a callee
/// body is written purely in terms of its {@link KernelArgumentNode}s. It is
/// only consumed when kernel invocations are lowered.
///
/// The descriptor's symbolic input domain is represented separately by a
/// VolumeNode. Call sites currently record only the degenerate per-item volume.
/// TODO: wire this metadata and the symbolic domain into invocation lowering.
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
                (IntegerType) TypeUtil.getValueType(graph, operands.getInt(0)),
                (IntegerType) TypeUtil.getValueType(graph, operands.getInt(1)),
                (IntegerType) TypeUtil.getValueType(graph, operands.getInt(2)),
                (IntegerType) TypeUtil.getValueType(graph, operands.getInt(3)),
                (IntegerType) TypeUtil.getValueType(graph, operands.getInt(4)),
                (IntegerType) TypeUtil.getValueType(graph, operands.getInt(5)),
                (IntegerType) TypeUtil.getValueType(graph, operands.getInt(6)),
                (IntegerType) TypeUtil.getValueType(graph, operands.getInt(7)),
                (IntegerType) TypeUtil.getValueType(graph, operands.getInt(8))
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
