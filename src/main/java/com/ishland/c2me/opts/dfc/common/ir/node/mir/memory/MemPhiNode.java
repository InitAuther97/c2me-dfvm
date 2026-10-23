package com.ishland.c2me.opts.dfc.common.ir.node.mir.memory;

import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeAccessor;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.PathSelectionNode;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.TypeUtil;

public class MemPhiNode extends PathSelectionNode implements MemOpNode {

    public static final MemPhiNode PHI = new MemPhiNode();

    private MemPhiNode() {}

    @Override
    protected EdgeAccessor getEdgeForSelection(IrGraph graph) {
        return graph.getMemory();
    }

    @Override
    protected IrType getTypeForSelection(IrGraph graph, int input) {
        return TypeUtil.getMemoryType(graph, input);
    }
}
