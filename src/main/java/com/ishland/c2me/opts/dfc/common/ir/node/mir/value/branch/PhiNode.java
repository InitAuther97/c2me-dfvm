package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.branch;

import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeAccessor;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.PathSelectionNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.ValueOpNode;
import com.ishland.c2me.opts.dfc.common.ir.type.IrType;
import com.ishland.c2me.opts.dfc.common.ir.type.util.TypeUtil;

public class PhiNode extends PathSelectionNode implements ValueOpNode {
    public static final PhiNode PHI = new PhiNode();

    protected PhiNode() {}

    @Override
    protected EdgeAccessor getEdgeForSelection(IrGraph graph) {
        return graph.getValue();
    }

    @Override
    protected IrType getTypeForSelection(IrGraph graph, int input) {
        return TypeUtil.getValueType(graph, input);
    }
}
