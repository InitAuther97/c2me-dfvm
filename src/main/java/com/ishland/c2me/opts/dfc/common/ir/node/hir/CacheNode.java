package com.ishland.c2me.opts.dfc.common.ir.node.hir;

import com.ishland.c2me.opts.dfc.common.ir.GraphContext;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.TypeUtil;
import com.ishland.flowsched.util.Assertions;

/// Cache annotation in an HIR value DAG. Value operand 0 is the input,
/// forwarded unchanged. The marker has no memory operands or runtime cost;
/// later passes decide how to materialize the cache.
/// Canonicalization preserves the marker while it has users.
public enum CacheNode implements MarkerHIRNode {

    INSTANCE;

    @Override
    public IrType compute(IrGraph graph, int id, GraphContext ctx) {
        final var operands = graph.getValue().getOperands(id);
        Assertions.assertTrue(operands.size() == 1, "cache must have exactly one value operand");
        return TypeUtil.getValueType(graph, operands.getInt(0));
    }
}
