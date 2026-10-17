package com.ishland.c2me.opts.dfc.common.ir.node.mir;

import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;

public interface LateExpansionNode extends MIRNode {
    void expand(IrGraph graph, int id);
}
