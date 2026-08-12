package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.branch;

import com.ishland.c2me.opts.dfc.common.ir.graph.EdgeAccessor;
import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.SelectionNode;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.ValueOpNode;

public class PhiNode extends SelectionNode implements ValueOpNode {
    public static final PhiNode PHI = new PhiNode();

    protected PhiNode() {}

    @Override
    protected EdgeAccessor getEdgeForSelection(IrGraph graph) {
        return graph.getValue();
    }
}
