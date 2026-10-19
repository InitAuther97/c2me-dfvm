package com.ishland.c2me.opts.dfc.common.ir.node.tree;

import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.IrNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;

public interface TreeIRNode extends IrNode {
    void lowerToHigh(IrGraph graph, int id, Canonicalizer canonicalizer);
}
