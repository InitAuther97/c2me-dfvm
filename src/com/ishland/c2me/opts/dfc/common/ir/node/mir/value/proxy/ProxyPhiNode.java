package com.ishland.c2me.opts.dfc.common.ir.node.mir.value.proxy;

import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.mir.value.branch.PhiNode;
import com.ishland.c2me.opts.dfc.common.ir.phase.scalar.Canonicalizer;

public class ProxyPhiNode extends PhiNode {
    public static final ProxyPhiNode PHI = new ProxyPhiNode();

    @Override
    protected boolean collapseSameInput(IrGraph graph, int id, Canonicalizer canonicalizer) {
        return false;
    }
}
