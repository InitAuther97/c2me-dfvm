package com.ishland.c2me.opts.dfc.common.ir.node.hir;

import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;

public interface HIRNode {
    void lowerToMid(IrGraph graph, int id, Canonicalizer canonicalizer);
}
